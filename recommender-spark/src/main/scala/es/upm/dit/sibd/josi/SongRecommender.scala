package es.upm.dit.sibd.josi

import org.apache.hadoop.fs.{FileSystem, Path}

import scala.util.Random
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.sql.functions._

object SongRecommender {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().getOrCreate()

    if (args.isEmpty ) {
      println("An argument is expected with userID value")
      System.exit(1)
    }

    val userID: Int = args[0].toInt

    val fsBase = "hdfs:///user/josi/data/"
    val rawUserArtistData = spark.read.textFile(fsBase + "user_artist_data.txt")
    val rawArtistData = spark.read.textFile(fsBase + "artist_data.txt")
    val rawArtistAlias = spark.read .textFile(fsBase + "artist_alias.txt")

    val recommender = new SongRecommender(spark)

    val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    val modelPath = new Path(fsBase + "model")
    val model:ALSModel = if (fs.exists(modelPath)) {
      println("Load existing model")
      ALSModel.load(modelPath.toString)
    }
    else {
      println("Train model with training data")
      val trainedModel = recommender.train(rawUserArtistData, rawArtistData, rawArtistAlias)
      trainedModel.save(modelPath.toString)
      trainedModel
    }

    println(s"Make a recommendation to user $userID")
    recommender.recommend(model, userID, 5, rawArtistData).show()
  }
}

class SongRecommender(private val spark: SparkSession) {

  import spark.implicits._

  def train(rawUserArtistData: Dataset[String],
            rawArtistData: Dataset[String],
            rawArtistAlias: Dataset[String]): ALSModel = {

    val bArtistAlias = spark.sparkContext.broadcast(buildArtistAlias(rawArtistAlias))

    val trainData = buildCounts(rawUserArtistData, bArtistAlias).cache()

    val model = new ALS()
      .setSeed(Random.nextLong)
      .setImplicitPrefs(true)
      .setRank(10)
      .setRegParam(1.0)
      .setAlpha(40.0)
      .setMaxIter(20)
      .setUserCol("user")
      .setItemCol("artist")
      .setRatingCol("count")
      .setPredictionCol("prediction")
      .fit(trainData)

    trainData.unpersist()

    model
  }

  def recommend(model: ALSModel, userID: Int, howMany: Int, rawArtistData: Dataset[String]): DataFrame = {
    val toRecommend = model.itemFactors.select($"id".as("artist")).withColumn("user", lit(userID))
    val topRecommendations = model.transform(toRecommend).select("artist", "prediction").orderBy($"prediction".desc).limit(howMany)

    val recommendedArtistIDs = topRecommendations.select("artist").as[Int].collect()
    val artistByID = buildArtistByID(rawArtistData)
    artistByID.join(spark.createDataset(recommendedArtistIDs).toDF("id"), "id").select("name")
  }

  private def buildArtistByID(rawArtistData: Dataset[String]): DataFrame = {
    rawArtistData.flatMap { line =>
      val (id, name) = line.span(_ != '\t')
      if (name.isEmpty) {
        None
      } else {
        try {
          Some((id.toInt, name.trim))
        } catch {
          case _: NumberFormatException => None
        }
      }
    }.toDF("id", "name")
  }

  private def buildArtistAlias(rawArtistAlias: Dataset[String]): Map[Int, Int] = {
    rawArtistAlias.flatMap { line =>
      val Array(artist, alias) = line.split('\t')
      if (artist.isEmpty) {
        None
      } else {
        Some((artist.toInt, alias.toInt))
      }
    }.collect().toMap
  }

  private def buildCounts(rawUserArtistData: Dataset[String],
                          bArtistAlias: Broadcast[Map[Int, Int]]): DataFrame = {
    rawUserArtistData.map { line =>
      val Array(userID, artistID, count) = line.split(' ').map(_.toInt)
      val finalArtistID = bArtistAlias.value.getOrElse(artistID, artistID)
      (userID, finalArtistID, count)
    }.toDF("user", "artist", "count")
  }
}
