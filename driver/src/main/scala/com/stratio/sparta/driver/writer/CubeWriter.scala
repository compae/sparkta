/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.sparta.driver.writer

import java.sql.{Date, Timestamp}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.driver.cube.Cube
import com.stratio.sparta.driver.factory.SparkContextFactory
import com.stratio.sparta.sdk._
import com.stratio.sparta.sdk.pipeline.aggregation.cube.{DimensionValue, DimensionValuesTime, MeasuresValues}
import com.stratio.sparta.sdk.pipeline.autoCalculations.AutoCalculatedField
import com.stratio.sparta.sdk.pipeline.output.{Output, SaveModeEnum}
import com.stratio.sparta.sdk.pipeline.schema.TypeOp
import org.apache.spark.sql._
import org.apache.spark.streaming.dstream.DStream

import scala.util.{Failure, Success, Try}

case class CubeWriter(cube: Cube, outputs: Seq[Output])
  extends TriggerWriter with SLF4JLogging {

  def write(stream: DStream[(DimensionValuesTime, MeasuresValues)]): Unit = {
    stream.map { case (dimensionValuesTime, measuresValues) =>
      toRow(dimensionValuesTime, measuresValues)
    }.foreachRDD(rdd => {
      if (!rdd.isEmpty()) {
        val sqlContext = SparkContextFactory.sparkSessionInstance
        val cubeDf = sqlContext.createDataFrame(rdd, cube.schema)
        val cubeAutoCalculatedFieldsDf =
          applyAutoCalculateFields(cubeDf, cube.cubeWriterOptions.autoCalculateFields, cube.schema)
        val outputTableName = cube.cubeWriterOptions.tableName.getOrElse(cube.name)
        val saveOptions = cube.expiringDataConfig.fold(Map.empty[String, String]) { expiringData =>
          Map(Output.TimeDimensionKey -> expiringData.timeDimension)
        } ++ Map(Output.TableNameKey -> outputTableName) ++
          cube.cubeWriterOptions.partitionBy.fold(Map.empty[String, String]) {partition =>
            Map(Output.PartitionByKey -> partition)} ++
          cube.cubeWriterOptions.primaryKey.fold(Map.empty[String, String]) {key =>
            Map(Output.PrimaryKey -> key)}

        cube.cubeWriterOptions.outputs.foreach(outputName =>
          outputs.find(output => output.name == outputName) match {
            case Some(outputWriter) => Try {
              outputWriter.save(cubeAutoCalculatedFieldsDf, cube.cubeWriterOptions.saveMode, saveOptions)
            } match {
              case Success(_) =>
                log.debug(s"Data stored in $outputTableName")
              case Failure(e) =>
                log.error(s"Something goes wrong. Table: $outputTableName")
                log.error(s"Schema. ${cubeAutoCalculatedFieldsDf.schema}")
                log.error(s"Head element. ${cubeAutoCalculatedFieldsDf.head}")
                log.error(s"Error message : ", e)
            }
            case None => log.error(s"The output in the cube : $outputName not match in the outputs")
          })

        writeTriggers(cubeAutoCalculatedFieldsDf, cube.triggers, cube.name, outputs)
      } else log.debug("Empty event received")
    })
  }

  def toRow(dimensionValuesT: DimensionValuesTime, measures: MeasuresValues): Row = {
    val measuresSorted = measuresValuesSorted(measures.values)
    val rowValues = dimensionValuesT.timeConfig match {
      case None =>
        val dimensionValues = dimensionsValuesSorted(dimensionValuesT.dimensionValues)

        dimensionValues ++ measuresSorted
      case Some(timeConfig) =>
        val timeValue = Seq(timeFromDateType(timeConfig.eventTime, cube.cubeWriterOptions.dateType))
        val dimFilteredByTime = filterDimensionsByTime(dimensionValuesT.dimensionValues, timeConfig.timeDimension)
        val dimensionValues = dimensionsValuesSorted(dimFilteredByTime) ++ timeValue
        val measuresValuesWithTime = measuresSorted

        dimensionValues ++ measuresValuesWithTime
    }

    Row.fromSeq(rowValues)
  }

  private def dimensionsValuesSorted(dimensionValues: Seq[DimensionValue]): Seq[Any] =
    dimensionValues.sorted.map(dimVal => dimVal.value)

  private def measuresValuesSorted(measures: Map[String, Option[Any]]): Seq[Any] =
    measures.toSeq.sortWith(_._1 < _._1).map(measure => measure._2.getOrElse(null))

  private def filterDimensionsByTime(dimensionValues: Seq[DimensionValue], timeDimension: String): Seq[DimensionValue] =
    dimensionValues.filter(dimensionValue => dimensionValue.dimension.name != timeDimension)

  private def timeFromDateType(time: Long, dateType: TypeOp.Value): Any = {
    dateType match {
      case TypeOp.Date | TypeOp.DateTime => new Date(time)
      case TypeOp.Long => time
      case TypeOp.Timestamp => new Timestamp(time)
      case _ => time.toString
    }
  }
}