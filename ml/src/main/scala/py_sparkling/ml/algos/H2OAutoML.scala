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
package py_sparkling.ml.algos

import ai.h2o.automl.AutoML
import org.apache.spark.ml.h2o.algos.H2OAutoMLReader
import org.apache.spark.ml.util.{MLReadable, MLReader}
import py_sparkling.ml.models.H2OMOJOModel
import water.support.ModelSerializationSupport

/**
  * H2O AutoML Wrapper for PySparkling
  */
class H2OAutoML(override val uid: String) extends org.apache.spark.ml.h2o.algos.H2OAutoML(uid) {

  override def trainModel(aml: AutoML): H2OMOJOModel = {
    new H2OMOJOModel(ModelSerializationSupport.getMojoData(aml.leader()))
  }
}

private[algos] object H2OAutoML extends MLReadable[H2OAutoML] {

  private final val defaultFileName = "automl_params"

  override def read: MLReader[H2OAutoML] = H2OAutoMLReader.create[H2OAutoML](defaultFileName)

  override def load(path: String): H2OAutoML = super.load(path)
}
