/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.spark.h2o.backends.external

import org.apache.spark.h2o.{H2OContext, H2OFrame}
import org.apache.spark.h2o.converters.WriteConverterCtx
import org.apache.spark.h2o.converters.WriteConverterCtxUtils.UploadPlan
import org.apache.spark.h2o.utils.SupportedTypes._
import org.apache.spark.h2o.utils.{NodeDesc, ReflectionUtils}
import org.apache.spark.ml.linalg.{DenseVector, SparseVector, Vector}
import org.apache.spark.sql.types._
import water._
import water.fvec.Frame

class ExternalWriteConverterCtx(nodeDesc: NodeDesc, writeTimeout: Int, driverTimeStamp: Short, blockSize: Long) extends WriteConverterCtx {

  private val externalFrameWriter = ExternalFrameWriterClient.create(nodeDesc.hostname, nodeDesc.port, driverTimeStamp, writeTimeout, blockSize)

  override def closeChunks(numRows: Int): Unit = {
    externalFrameWriter.close()
  }

  override def initFrame(key: String, columns: Array[String]): Unit = {
    externalFrameWriter.initFrame(key, columns)
  }

  override def finalizeFrame(key: String, rowsPerChunk: Array[Long], colTypes: Array[Byte], domains: Array[Array[String]] = null): Unit = {
    externalFrameWriter.finalizeFrame(key, rowsPerChunk, colTypes, domains)
  }

  /**
    * Initialize the communication before the chunks are created
    */
  override def createChunk(keystr: String, numRows: Option[Int], expectedTypes: Array[Byte], chunkId: Int, maxVecSizes: Array[Int],
                           sparse: Array[Boolean], vecStartSize: Map[Int, Int]): Unit = {
    externalFrameWriter.createChunk(keystr, expectedTypes, chunkId, numRows.get, maxVecSizes)
  }

  override def put(colIdx: Int, data: Boolean) = externalFrameWriter.sendBoolean(data)

  override def put(colIdx: Int, data: Byte) = externalFrameWriter.sendByte(data)

  override def put(colIdx: Int, data: Char) = externalFrameWriter.sendChar(data)

  override def put(colIdx: Int, data: Short) = externalFrameWriter.sendShort(data)

  override def put(colIdx: Int, data: Int) = externalFrameWriter.sendInt(data)

  override def put(colIdx: Int, data: Long) = externalFrameWriter.sendLong(data)

  override def put(colIdx: Int, data: Float) = externalFrameWriter.sendFloat(data)

  override def put(colIdx: Int, data: Double) = externalFrameWriter.sendDouble(data)

  override def put(colIdx: Int, data: java.sql.Timestamp) = externalFrameWriter.sendTimestamp(data)

  // Here we should call externalFrameWriter.sendDate - however such method does not exist
  // Hence going through the same path as sending Timestamp long value.
  override def put(colIdx: Int, data: java.sql.Date) = externalFrameWriter.sendLong(data.getTime)

  override def put(colIdx: Int, data: String) = externalFrameWriter.sendString(data)

  override def putNA(columnNum: Int) = externalFrameWriter.sendNA()

  override def numOfRows(): Int = externalFrameWriter.getNumberOfWrittenRows

  override def putSparseVector(startIdx: Int, vector: SparseVector, maxVecSize: Int): Unit = {
    externalFrameWriter.sendSparseVector(vector.indices, vector.values)
  }

  override def putDenseVector(startIdx: Int, vector: DenseVector, maxVecSize: Int): Unit = {
    externalFrameWriter.sendDenseVector(vector.values)
  }

  override def startRow(rowIdx: Int): Unit = {}

  override def finishRow(): Unit = {}
}


object ExternalWriteConverterCtx {

  import scala.language.postfixOps

  def scheduleUpload(numPartitions: Int): UploadPlan = {
    val nodes = H2OContext.ensure("H2OContext needs to be running").getH2ONodes()
    val uploadPlan = (0 until numPartitions).zip(Stream.continually(nodes).flatten).toMap
    uploadPlan
  }

  def internalJavaClassOf(dt: DataType): Class[_] = {
    dt match {
      case n if n.isInstanceOf[DecimalType] & n.getClass.getSuperclass != classOf[DecimalType] => Double.javaClass
      case _: DateType => Long.javaClass
      case _: UserDefinedType[_ /*ml.linalg.Vector */ ] => classOf[Vector]
      case _: DataType => ReflectionUtils.supportedTypeOf(dt).javaClass
    }
  }
}
