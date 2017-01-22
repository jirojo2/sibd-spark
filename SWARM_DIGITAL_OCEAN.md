# Docker Swarm setup instructions for DigitalOcean provider

Make sure to define the variable `$DO_TOKEN` with the DigitalOcean API token.

```bash
docker-machine create \
  --driver=digitalocean \
  --digitalocean-access-token=$DO_TOKEN \
  --digitalocean-size=512mb \
  --digitalocean-region=ams3 \
  --digitalocean-private-networking=true \
  --digitalocean-image=ubuntu-16-04-x64 \
    swarm-kv-store

docker $(docker-machine config swarm-kv-store) run -d \
  --net=host progrium/consul --server -bootstrap-expect 1

kvip=$(docker-machine ip swarm-kv-store)

docker-machine create \
  --driver=digitalocean \
  --digitalocean-access-token=$DO_TOKEN \
  --digitalocean-size=4gb \
  --digitalocean-region=ams3 \
  --digitalocean-private-networking=true \
  --digitalocean-image=ubuntu-16-04-x64 \
  --swarm \
  --swarm-master \
  --swarm-discovery consul://${kvip}:8500 \
  --engine-opt "cluster-store consul://${kvip}:8500" \
  --engine-opt "cluster-advertise eth1:2376" \
    swarm-manager

docker-machine create \
  --driver=digitalocean \
  --digitalocean-access-token=$DO_TOKEN \
  --digitalocean-size=4gb \
  --digitalocean-region=ams3 \
  --digitalocean-private-networking=true \
  --digitalocean-image=ubuntu-16-04-x64 \
  --swarm \
  --swarm-discovery consul://${kvip}:8500 \
  --engine-opt "cluster-store consul://${kvip}:8500" \
  --engine-opt "cluster-advertise eth1:2376" \
    swarm-node-01

docker-machine create \
  --driver=digitalocean \
  --digitalocean-access-token=$DO_TOKEN \
  --digitalocean-size=4gb \
  --digitalocean-region=ams3 \
  --digitalocean-private-networking=true \
  --digitalocean-image=ubuntu-16-04-x64 \
  --swarm \
  --swarm-discovery consul://${kvip}:8500 \
  --engine-opt "cluster-store consul://${kvip}:8500" \
  --engine-opt "cluster-advertise eth1:2376" \
    swarm-node-02

eval $(docker-machine env --swarm swarm-manager)
docker info
```