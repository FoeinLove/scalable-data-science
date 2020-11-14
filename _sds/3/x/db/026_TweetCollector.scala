// Databricks notebook source
// MAGIC %md
// MAGIC # [ScaDaMaLe, Scalable Data Science and Distributed Machine Learning](https://lamastex.github.io/scalable-data-science/sds/3/x/)

// COMMAND ----------

// MAGIC %md
// MAGIC # Tweet Collector - capture live tweets
// MAGIC 
// MAGIC Here are the main steps in this notebook:
// MAGIC 
// MAGIC 1. let's collect from the public twitter stream and write to DBFS as json strings in a boiler-plate manner to understand the componets better.
// MAGIC * Then we will turn the collector into a function and use it
// MAGIC * Finally we will use some DataFrame-based pipelines to convert the raw tweets into other structured content.

// COMMAND ----------

// MAGIC %md
// MAGIC We will call extendedTwitterUtils notebook from here. 
// MAGIC 
// MAGIC But **first install** the following libraries from maven central and attach to this cluster:
// MAGIC 
// MAGIC * gson with maven coordinates `com.google.code.gson:gson:2.8.4` 
// MAGIC * twitter4j-examples with maven coordinates `org.twitter4j:twitter4j-examples:4.0.7`

// COMMAND ----------

// MAGIC %run "./025_a_extendedTwitterUtils2run"

// COMMAND ----------

// MAGIC %md
// MAGIC Go to SparkUI and see if a streaming job is already running. If so you need to terminate it before starting a new streaming job. Only one streaming job can be run on the DB CE.

// COMMAND ----------

// this will make sure all streaming job in the cluster are stopped
StreamingContext.getActive.foreach{ _.stop(stopSparkContext = false) }

// COMMAND ----------

// MAGIC %md
// MAGIC Let's create a directory in dbfs for storing tweets in the cluster's distributed file system.

// COMMAND ----------

val outputDirectoryRoot = "/datasets/tweetsStreamTmp" // output directory

// COMMAND ----------

dbutils.fs.mkdirs("/datasets/tweetsStreamTmp")

// COMMAND ----------

display(dbutils.fs.ls(outputDirectoryRoot))

// COMMAND ----------

// to remove a pre-existing directory and start from scratch uncomment next line and evaluate this cell
//dbutils.fs.rm("/datasets/tweetsStreamTmp", true) 

// COMMAND ----------

// MAGIC %md
// MAGIC Capture tweets in every sliding window of `slideInterval` many milliseconds.

// COMMAND ----------

val slideInterval = new Duration(1 * 1000) // 1 * 1000 = 1000 milli-seconds = 1 sec

// COMMAND ----------

// MAGIC %md
// MAGIC Recall that **Discretized Stream** or **DStream** is the basic abstraction provided
// MAGIC by Spark Streaming. It represents a continuous stream of data, either
// MAGIC the input data stream received from source, or the processed data stream
// MAGIC generated by transforming the input stream. Internally, a DStream is
// MAGIC represented by a continuous series of RDDs, which is Spark?s abstraction
// MAGIC of an immutable, distributed dataset (see [Spark Programming
// MAGIC Guide](http://spark.apache.org/docs/latest/programming-guide.html#resilient-distributed-datasets-rdds)
// MAGIC for more details). Each RDD in a DStream contains data from a certain
// MAGIC interval, as shown in the following figure.
// MAGIC 
// MAGIC ![Spark
// MAGIC Streaming](http://spark.apache.org/docs/latest/img/streaming-dstream.png "Spark Streaming data flow")

// COMMAND ----------

// MAGIC %md
// MAGIC Let's import google's json library next.

// COMMAND ----------

import com.google.gson.Gson 

// COMMAND ----------

// MAGIC %md
// MAGIC Our goal is to take each RDD in the twitter DStream and write it as a json file in our dbfs.

// COMMAND ----------

// Create a Spark Streaming Context.
val ssc = new StreamingContext(sc, slideInterval)

// COMMAND ----------

// MAGIC %md
// MAGIC ## CAUTION
// MAGIC Extracting knowledge from tweets is "easy" using techniques shown here, but one has to take legal responsibility for the use of this knowledge and conform to the rules and policies linked below.
// MAGIC 
// MAGIC Remeber that the use of twitter itself comes with various strings attached. Read:
// MAGIC 
// MAGIC - [Twitter Rules](https://twitter.com/rules)
// MAGIC 
// MAGIC 
// MAGIC Crucially, the use of the content from twitter by you (as done in this worksheet) comes with some strings.  Read:
// MAGIC 
// MAGIC - [Developer Agreement & Policy Twitter Developer Agreement](https://dev.twitter.com/overview/terms/agreement-and-policy)
// MAGIC 
// MAGIC ### Enter your own Twitter API Credentials.
// MAGIC 
// MAGIC * Go to https://apps.twitter.com and look up your Twitter API Credentials, or create an app to create them.
// MAGIC * Get your own Twitter API Credentials: `consumerKey`, `consumerSecret`, `accessToken` and `accessTokenSecret` and enter them in the cell below.
// MAGIC 
// MAGIC ### Ethical/Legal Aspects
// MAGIC 
// MAGIC See Background Readings/Viewings in Project MEP:
// MAGIC 
// MAGIC * [https://lamastex.github.io/scalable-data-science/sds/research/mep/](https://lamastex.github.io/scalable-data-science/sds/research/mep/)

// COMMAND ----------

// MAGIC %md
// MAGIC 
// MAGIC ### First Step to Do Your Own Experiments in Twitter: Enter your Twitter API Credentials.
// MAGIC * Go to https://apps.twitter.com and look up your Twitter API Credentials, or create an app to create them.
// MAGIC * Run the code in a cell to Enter your own credentials.
// MAGIC 
// MAGIC ```%scala
// MAGIC // put your own twitter developer credentials below instead of xxx
// MAGIC // instead of the '%run ".../secrets/026_secret_MyTwitterOAuthCredentials"' below
// MAGIC // you need to copy-paste the following code-block with your own Twitter credentials replacing XXXX
// MAGIC 
// MAGIC 
// MAGIC // put your own twitter developer credentials below 
// MAGIC 
// MAGIC import twitter4j.auth.OAuthAuthorization
// MAGIC import twitter4j.conf.ConfigurationBuilder
// MAGIC 
// MAGIC 
// MAGIC // These have been regenerated!!! - need to chane them
// MAGIC 
// MAGIC def myAPIKey       = "XXXX" // APIKey 
// MAGIC def myAPISecret    = "XXXX" // APISecretKey
// MAGIC def myAccessToken          = "XXXX" // AccessToken
// MAGIC def myAccessTokenSecret    = "XXXX" // AccessTokenSecret
// MAGIC 
// MAGIC 
// MAGIC System.setProperty("twitter4j.oauth.consumerKey", myAPIKey)
// MAGIC System.setProperty("twitter4j.oauth.consumerSecret", myAPISecret)
// MAGIC System.setProperty("twitter4j.oauth.accessToken", myAccessToken)
// MAGIC System.setProperty("twitter4j.oauth.accessTokenSecret", myAccessTokenSecret)
// MAGIC 
// MAGIC println("twitter OAuth Credentials loaded")
// MAGIC 
// MAGIC ```
// MAGIC 
// MAGIC The cell-below will not expose my Twitter API Credentials: `myAPIKey`, `myAPISecret`, `myAccessToken` and `myAccessTokenSecret`. Use the code above to enter your own credentials in a scala cell.

// COMMAND ----------

// MAGIC %run "Users/raazesh.sainudiin@math.uu.se/scalable-data-science/secrets/026_secret_MyTwitterOAuthCredentials"

// COMMAND ----------

// Create a Twitter Stream for the input source. 
val auth = Some(new OAuthAuthorization(new ConfigurationBuilder().build()))
val twitterStream = ExtendedTwitterUtils.createStream(ssc, auth)

// COMMAND ----------

// MAGIC %md
// MAGIC Let's map the tweets into json formatted string (one tweet per line).

// COMMAND ----------

//This allows easy embedding of publicly available information into any other notebook
//Example usage:
// displayHTML(frameIt("https://en.wikipedia.org/wiki/Latent_Dirichlet_allocation#Topics_in_LDA",250))
def frameIt( u:String, h:Int ) : String = {
      """<iframe 
 src=""""+ u+""""
 width="95%" height="""" + h + """">
  <p>
    <a href="http://spark.apache.org/docs/latest/index.html">
      Fallback link for browsers that, unlikely, don't support frames
    </a>
  </p>
</iframe>"""
   }
displayHTML(frameIt("https://en.wikipedia.org/wiki/JSON",400))

// COMMAND ----------

val twitterStreamJson = twitterStream.map(
                                            x => { val gson = new Gson();
                                                 val xJson = gson.toJson(x)
                                                 xJson
                                                 }
                                          ) 

// COMMAND ----------

outputDirectoryRoot

// COMMAND ----------

var numTweetsCollected = 0L // track number of tweets collected
val partitionsEachInterval = 1 // This tells the number of partitions in each RDD of tweets in the DStream.

twitterStreamJson.foreachRDD( 
  (rdd, time) => { // for each RDD in the DStream
      val count = rdd.count()
      if (count > 0) {
        val outputRDD = rdd.repartition(partitionsEachInterval) // repartition as desired
        outputRDD.saveAsTextFile(outputDirectoryRoot + "/tweets_" + time.milliseconds.toString) // save as textfile
        numTweetsCollected += count // update with the latest count
      }
  }
)

// COMMAND ----------

// MAGIC %md 
// MAGIC Nothing has actually happened yet.
// MAGIC 
// MAGIC Let's start the spark streaming context we have created next.

// COMMAND ----------

ssc.start()

// COMMAND ----------

// MAGIC %md
// MAGIC Let's look at the spark UI now and monitor the streaming job in action!  Go to `Clusters` on the left and click on `UI` and then `Streaming`.

// COMMAND ----------

numTweetsCollected // number of tweets collected so far

// COMMAND ----------

// MAGIC %md
// MAGIC Let's try seeing again in a few seconds how many tweets have been collected up to now.

// COMMAND ----------

numTweetsCollected // number of tweets collected so far

// COMMAND ----------

// MAGIC %md
// MAGIC Note that you could easilt fill up disk space!!!
// MAGIC 
// MAGIC So let's stop the streaming job next.

// COMMAND ----------

ssc.stop(stopSparkContext = false) // gotto stop soon!!!

// COMMAND ----------

// MAGIC %md
// MAGIC Let's make sure that the `Streaming` UI is not active in the `Clusters` `UI`.

// COMMAND ----------

StreamingContext.getActive.foreach { _.stop(stopSparkContext = false) } // extra cautious stopping of all active streaming contexts

// COMMAND ----------

// MAGIC %md
// MAGIC ## Let's examine what was saved in dbfs

// COMMAND ----------

display(dbutils.fs.ls(outputDirectoryRoot))

// COMMAND ----------

val tweetsDir = outputDirectoryRoot+"/tweets_1605358954000/" // use an existing file, may have to rename folder based on output above!

// COMMAND ----------

display(dbutils.fs.ls(tweetsDir)) 

// COMMAND ----------

sc.textFile(tweetsDir+"part-00000").count()

// COMMAND ----------

val outJson = sqlContext.read.json(tweetsDir+"part-00000")

// COMMAND ----------

outJson.printSchema()

// COMMAND ----------

outJson.select("id","text").show(false) // output not displayed to comply with Twitter Developer rules

// COMMAND ----------

display(outJson)  // output not displayed to comply with Twitter Developer rules

// COMMAND ----------

// MAGIC %md
// MAGIC Now, let's be good at house-keeping and clean-up the unnecessary data in dbfs, our distributed file system (in databricks).

// COMMAND ----------

// to remove a pre-existing directory and start from scratch uncomment next line and evaluate this cell
//dbutils.fs.rm(outputDirectoryRoot, true) 

// COMMAND ----------

// MAGIC %md
// MAGIC Clearly there is a lot one can do with tweets!
// MAGIC 
// MAGIC Enspecially, after you can get a few more primitives under your belt from the following areas:
// MAGIC 
// MAGIC * Natural Language Processing (MLlib, beyond word counts of course), 
// MAGIC * Distributed vertex programming (Graph Frames, which you already know), and 
// MAGIC * Scalable geospatial computing with location data on open street maps (roughly a third of tweets are geo-enabled with Latitude and Longitude of the tweet location) -  we will get into this.

// COMMAND ----------

// MAGIC %md
// MAGIC ### Making a function for Spark Streaming job
// MAGIC 
// MAGIC Let's try to throw the bits and bobs of code above into a function called `streamFunc` for simplicity and modularity.

// COMMAND ----------

import com.google.gson.Gson 
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

val outputDirectoryRoot = "/datasets/tweetsStreamTmp" // output directory
val batchInterval = 1 // in minutes
val timeoutJobLength =  batchInterval * 5

var newContextCreated = false
var numTweetsCollected = 0L // track number of tweets collected
//val conf = new SparkConf().setAppName("TrackedTweetCollector").setMaster("local")
// This is the function that creates the SteamingContext and sets up the Spark Streaming job.
def streamFunc(): StreamingContext = {
  // Create a Spark Streaming Context.
  val ssc = new StreamingContext(sc, Minutes(batchInterval))
  // Create the OAuth Twitter credentials 
  val auth = Some(new OAuthAuthorization(new ConfigurationBuilder().build()))
  // Create a Twitter Stream for the input source.  
  val twitterStream = ExtendedTwitterUtils.createStream(ssc, auth)
  // Transform the discrete RDDs into JSON
  val twitterStreamJson = twitterStream.map(x => { val gson = new Gson();
                                                 val xJson = gson.toJson(x)
                                                 xJson
                                               }) 
  // take care
  val partitionsEachInterval = 1 // This tells the number of partitions in each RDD of tweets in the DStream.
  
  // what we want done with each discrete RDD tuple: (rdd, time)
  twitterStreamJson.foreachRDD((rdd, time) => { // for each filtered RDD in the DStream
      val count = rdd.count()
      if (count > 0) {
        val outputRDD = rdd.repartition(partitionsEachInterval) // repartition as desired
        // to write to parquet directly in append mode in one directory per 'time'------------       
        val outputDF = outputRDD.toDF("tweetAsJsonString")
        // get some time fields from current `.Date()`
        val year = (new java.text.SimpleDateFormat("yyyy")).format(new java.util.Date())
        val month = (new java.text.SimpleDateFormat("MM")).format(new java.util.Date())
        val day = (new java.text.SimpleDateFormat("dd")).format(new java.util.Date())
        val hour = (new java.text.SimpleDateFormat("HH")).format(new java.util.Date())
        // write to a file with a clear time-based hierarchical directory structure for example
        outputDF.write.mode(SaveMode.Append)
                .parquet(outputDirectoryRoot+ "/"+ year + "/" + month + "/" + day + "/" + hour + "/" + time.milliseconds) 
        // end of writing as parquet file-------------------------------------
        numTweetsCollected += count // update with the latest count
      }
  })
  newContextCreated = true
  ssc
}

// COMMAND ----------

// Now just use the function to create a Spark Streaming Context
val ssc = StreamingContext.getActiveOrCreate(streamFunc)

// COMMAND ----------

// you only need one of these to start
ssc.start()
//ssc.awaitTerminationOrTimeout(timeoutJobLength)

// COMMAND ----------

// this will make sure all streaming job in the cluster are stopped
// but let' run it for a few minutes before stopping it
StreamingContext.getActive.foreach { _.stop(stopSparkContext = false) } 

// COMMAND ----------

display(dbutils.fs.ls("/datasets/tweetsStreamTmp/2020/11/14/13/")) // outputDirectoryRoot

// COMMAND ----------

// MAGIC %md
// MAGIC Next, let us take a quick peek at the notebook `./025_b_TTTDFfunctions` to see how we have pipelined the JSON tweets into DataFrames. 
// MAGIC 
// MAGIC Please see [http://lamastex.org/lmse/mep/src/TweetAnatomyAndTransmissionTree.html](http://lamastex.org/lmse/mep/src/TweetAnatomyAndTransmissionTree.html) to understand more deeply.

// COMMAND ----------

// MAGIC %run "./025_b_TTTDFfunctions"

// COMMAND ----------

val rawDF = fromParquetFile2DF("/datasets/tweetsStreamTmp/2020/11/*/*/*/*") //.cache()
val TTTsDF = tweetsDF2TTTDF(tweetsJsonStringDF2TweetsDF(rawDF)).cache()

// COMMAND ----------

TTTsDF.count()

// COMMAND ----------

//display(TTTsDF)  // output not displayed to comply with Twitter Developer rules

// COMMAND ----------

TTTsDF.printSchema

// COMMAND ----------

display(TTTsDF.groupBy($"tweetType").count().orderBy($"count".desc))

// COMMAND ----------

// this will make sure all streaming job in the cluster are stopped
StreamingContext.getActive.foreach{ _.stop(stopSparkContext = false) } 

// COMMAND ----------

// this will delete what we collected to keep the disk usage tight and tidy
dbutils.fs.rm(outputDirectoryRoot, true) 

// COMMAND ----------

// MAGIC %md
// MAGIC 
// MAGIC %md
// MAGIC ## Next, let's write the tweets into a scalable commercial cloud storage system
// MAGIC 
// MAGIC We will make sure to write the tweets to AWS's simple storage service or S3, a scalable storage system in the cloud. See [https://aws.amazon.com/s3/](https://aws.amazon.com/s3/).
// MAGIC 
// MAGIC **skip this section if you don't have AWS account**.
// MAGIC 
// MAGIC But all the main syntactic bits are here for your future convenience :)
// MAGIC 
// MAGIC ```
// MAGIC // Replace with your AWS S3 credentials
// MAGIC //
// MAGIC // NOTE: Set the access to this notebook appropriately to protect the security of your keys.
// MAGIC // Or you can delete this cell after you run the mount command below once successfully.
// MAGIC 
// MAGIC val AccessKey = getArgument("1. ACCESS_KEY", "REPLACE_WITH_YOUR_ACCESS_KEY")
// MAGIC val SecretKey = getArgument("2. SECRET_KEY", "REPLACE_WITH_YOUR_SECRET_KEY")
// MAGIC val EncodedSecretKey = SecretKey.replace("/", "%2F")
// MAGIC val AwsBucketName = getArgument("3. S3_BUCKET", "REPLACE_WITH_YOUR_S3_BUCKET")
// MAGIC val MountName = getArgument("4. MNT_NAME", "REPLACE_WITH_YOUR_MOUNT_NAME")
// MAGIC val s3Filename = "tweetDump"
// MAGIC ```
// MAGIC 
// MAGIC Now just mount s3 as follows:
// MAGIC 
// MAGIC ```
// MAGIC dbutils.fs.mount(s"s3a://$AccessKey:$EncodedSecretKey@$AwsBucketName", s"/mnt/$MountName")
// MAGIC ```
// MAGIC 
// MAGIC Now you can use the `dbutils` commands freely to access data in the mounted S3.
// MAGIC 
// MAGIC ```
// MAGIC dbutils.fs.help()
// MAGIC ```
// MAGIC 
// MAGIC copying:
// MAGIC ```
// MAGIC // to copy all the tweets to s3
// MAGIC dbutils.fs.cp("dbfs:/rawTweets",s"/mnt/$MountName/rawTweetsInS3/",recurse=true) 
// MAGIC ```
// MAGIC 
// MAGIC deleting:
// MAGIC ```
// MAGIC // to remove all the files from s3
// MAGIC dbutils.fs.rm(s"/mnt/$MountName/rawTweetsInS3",recurse=true) 
// MAGIC ```
// MAGIC 
// MAGIC unmounting:
// MAGIC ```
// MAGIC // finally unmount when done - IMPORTANT!
// MAGIC dbutils.fs.unmount(s"/mnt/$MountName") 
// MAGIC ```