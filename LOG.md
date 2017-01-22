# Recommender Development Log

Lo primero es el desarrollo de la aplicación en cuestión.

Los requisitos establecen que dicha aplicación deberá implementarse con Apache Spark, Scala
y, teniendo en cuenta las mejoras, con HDFS y algún negociador de recursos.

Elegimos la versión 2.11.8 de Scala, y las versiones 2.1.0 de Spark y 2.7.3 de Hadoop por ser las más recientes.
Usaremos el negociador de recursos Hadoop YARN, que viene integrado tanto con Spark como Hadoop.

La aplicación utiliza el algoritmo de filtrado colectivo ALS que viene con Spark MLlib
(de hecho, es el único algoritmo de este tipo que viene por defecto con esta librería).

A partir de los datos proporcionados por la compañía Audioscrobbler, que cargamos en el espacio de usuario
'josi' de nuestro HDFS, aplicamos el algoritmo ALS y obtenemos un modelo. Guardamos dicho modelo en HDFS
(dos RDD de features, Spark da soporte para guardar y recuperar el modelo matricial de forma trivial) y lo
reutilizaremos para posteriores recomendaciones, suponiendo que no han cambiado los datos fuente del modelo.
Una vez calculado el modelo, obtenemos las mejores recomendaciones (ordenadas por "probabilidad de acierto")
para el vector de características concreto del usuario al que queremos recomendar. Este resultado será un
vector de N tuplas con un identificador y un número que representa una probabilidad de acierto. A partir de
los datos de artistas, transformamos los identificadores obtenidos en los nombres canónicos (puede haber
varios identificadores que representen al mismo artisa, con variaciones simples en el nombre, utilizaremos otra
lista que tenemos en HDFS para identificar el nombre canónico correspondiente) y por último filtramos el caso
de que el artista recomendado no esté correctamente aprovisionado.

El código scala se compila en un JAR mediante la herramienta SBT (una alternativa sería utilizar maven, por ejemplo).
Y el JAR empaquetado se ejecuta en Spark mediante la utilidad spark-submit.

Ahora hablaremos del entorno donde se despliega Spark. Hay varias alternativas.

La primera alternativa es utilizar el modo clúster standalone de Spark, ya que el propio Spark provee de
utilidades para desplegar un cluster especializado y distribuido.

Otra alternativa es utilizar el proyecto Apache Mesos, que nos permite abstraer todo un centro de datos a un
pool de recursos de forma transparente.

La última alternativa, que es la elegida, es utilizar el negociador de recursos YARN de Hadoop. Yarn conforma
un cluster especializado en tareas Hadoop, entre las que se encuentran las tareas Spark. Ademása provee de un
sistema de ficheros HDFS que utlizaremos desde Spark para leer los datos y guardar el modelo calculado.

Para conseguir un correcto despliegue, el primer paso es conseguir hacer funcionar Spark en modo standalone en un
entorno de desarrolo. Una vez conseguido eso, replicamos el caso en un entorno Yarn pseudo-distribuido (en mi entorno
de desarrollo, que consta de un solo nodo). Una vez familiarizados con el stack, replicamos el despliegue utilizando
contenedores docker, orquestrados por docker-compose.

Hadoop no comparte las ideas de despliegue de docker, por lo que tendremos que configurar nuestro clúster Hadoop de una
manera algo peculiar. Hadoop cuenta con que cada nodo dispone de mucha memoria y que abarcará el máximo de CPU, pero docker
está planteado para multiplexar muchos contendores en el mismo nodo. Además, Hadoop cuenta con que cada nodo tiene una IP
fija y que todos los nodos conocen la IP del resto de nodos y que el hostname asociado al nodo siempre resolverá a la IP del nodo.
Esto último es particularmente problemático con docker, y aún más cuando intentemos interactuar desde fuera o con docker swarm.

Antes de introducir la capa de abstracción docker swarm, que nos permitiría escalar de forma sencilla el número de nodos
en nuestro clúster Hadoop, observamos que a partir de la versión 1.12 de docker, el concepto de swarm se desdobla en dos
vertientes: legacy y swarm-mode. Resulta que recientemente el swarm legacy ha sido deprecado en favor de un nuevo modo swarm
en el core de docker, introduciendo un concepto de servicios que contrasta con la visión de cliente de los contendores convencionales.
El soporte para este nuevo modo swarm es todavía muy limitado, y no hay forma de orquestarlo con docker-compose o similares aún, por lo que
optaremos por utilizar el swam legacy.

Para simular un entorno multi nodo, utilizaremos docker-machine con un driver de KVM (puede usarse virtualbox, aws, digitalocean, o cualquier otro driver)
para aprovisionar un total de cuatro máquinas virtuales. La primera cobra un papel importante, ya que hospedará un contenedor que gestionará
el repositorio de claves distribuído (keystore) que gestionará las comunicaciones entre los diferentes nodos del swarm.

Las otras tres máquinas virtuales conformarán el swarm, siendo una de ellas el master del clúster, y las otras dos sendos nodos.
Todas estas máquinas contienen docker engine versión 1.12.6 en el momento de escribir este documento.

Cada nodo del swarm ejecuta un contenedor que gestiona la incorporación al swarm, y el nodo maestro ejecuta además otro contendor que gestiona
el cluster.

Mediante el modo swarm de docker-machine, nos conectamos al swarm de forma transparente, sin necesidad de interactuar con cada nodo,
y utilizamos docker-compose de forma natural (hay algunas limitaciones especificadas en la documentación, pero no nos afectarán en absoluto).

De todas formas, hay que tener muy presente que la red multi nodo en docker funciona de forma peculiar. El driver que utilizaremos para la red
es el driver overlay, que dará conectividad transparente a todos nuestros nodos pero siempre de forma aislada.

Al lanzar nuestro escenario con docker-compose, se creará una red overlay aislada para nuestro clúster, que desplegará un nodo Yarn con HDFS en cada
uno de los nodos del swarm. De este modo controlamos mediante condiciones en qué nodo del swarm se despliega cada nodo Yarn, y no lo dejamos al 
criterio del swarm, que suele utilizar una estrategia que minimiza el número de contenedores simultáneos que se ejecutan en cada nodo.

De esta forma, garantizamos que cada nodo disponible en el swarm, contenga un único nodo de Yarn, y que no habrá conflictos de puertos ni de hostnames.

De hecho, como utilizamos una doble capa de virtualización, cambia la resolución del hostname entre ambas capas (KVM y docker), por lo que tendremos
que indicar a hadoop que no resuelva la IP del dominio en el primer paso, sino que utilice el hostname en cada paso. Esto se consigue poniendo a true
la variable dfs.client.use.datanode.hostname en el fichero de configuración hdfs-site.xml.

Ya tenemos desplegado nuestro cluster Yarn con tres nodos, y resolvemos correctamente el hostname de los mismos desde el entorno de Spark.
También tenemos preparado nuestro JAR con la aplicación Spark. Sólo falta configurar el entorno de Spark para que lance la aplicación en modo
yarn-cluster contra el namenode maestro del cluster Yarn. Spark se encarga del resto.

Como crítica a la fama que ha conseguido docker estos últimos años, no todos los sitemas comulgan con la filosofía que sigue docker
a la hora de abstraer recursos. Este es un claro ejemplo, donde intentamos desplegar en docker un software (Hadoop) que está preparado
para exprimir el host que lo alberga al máximo. Una alternativa más acertada sería aprovisionar una serie de nodos iguales y configurarlos
con Ansible con un JDK y el entorno Hadoop, estableciendo un DNS en condiciones que resuelva para toda la red corporativa. Estos nodos
estarían dedicados exclusivamente a Hadoop, que es un concepto que choca frontalmente con la filosofía de docker. Aún así, un despliegue
en docker funciona, y permite compartir el nodo de forma sencilla y controlada.

