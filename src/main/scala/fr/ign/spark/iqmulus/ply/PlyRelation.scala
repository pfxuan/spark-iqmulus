/*
 * Copyright 2015 IGN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.ign.spark.iqmulus.ply

import fr.ign.spark.iqmulus.{ BinarySectionRelation, BinarySection }
import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.types._
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.sql.sources.OutputWriterFactory

class PlyRelation(
  override val paths: Array[String],
  dataSchemaOpt: Option[StructType],
  partitionColumns: Option[StructType],
  parameters: Map[String, String]
)(@transient val sqlContext: SQLContext)
    extends BinarySectionRelation(dataSchemaOpt, partitionColumns, parameters) {

  val element = parameters.getOrElse("element", "vertex")
  val littleEndian = parameters.getOrElse("littleEndian", "true").toBoolean

  lazy val headers: Array[PlyHeader] = paths flatMap { location =>
    val path = new Path(location)
    val fs = FileSystem.get(path.toUri, sqlContext.sparkContext.hadoopConfiguration)
    try {
      val dis = fs.open(path)
      try PlyHeader.read(location, dis)
      finally dis.close
    } catch {
      case _: java.io.FileNotFoundException =>
        logWarning(s"File not found : $location, skipping"); None
    }
  }

  override def sections: Array[BinarySection] =
    headers.flatMap(_.section.get(element))

  override def prepareJobForWrite(job: Job): OutputWriterFactory = {
    new PlyOutputWriterFactory(element, littleEndian)
  }
}

