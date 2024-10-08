package org.sunbird.obsrv.registry

import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset, DatasetSourceConfig, DatasetTransformation}
import org.sunbird.obsrv.service.DatasetRegistryService

import java.sql.Timestamp
import scala.collection.mutable

object DatasetRegistry {

  lazy private val datasets: mutable.Map[String, Dataset] = mutable.Map[String, Dataset]()
  datasets ++= DatasetRegistryService.readAllDatasets()
  lazy private val datasetTransformations: Map[String, List[DatasetTransformation]] = DatasetRegistryService.readAllDatasetTransformations()

  def getAllDatasets(datasetType: Option[String]): List[Dataset] = {
    val datasetList = DatasetRegistryService.readAllDatasets()
    if(datasetType.isDefined) {
      datasetList.filter(f => f._2.datasetType.equals(datasetType.get)).values.toList
    } else {
      datasetList.values.toList
    }

  }

  def getDataset(id: String): Option[Dataset] = {
    val datasetFromCache = datasets.get(id)
    if (datasetFromCache.isDefined) datasetFromCache else {
      val dataset = DatasetRegistryService.readDataset(id)
      if (dataset.isDefined) datasets.put(dataset.get.id, dataset.get)
      dataset
    }
  }

  def getAllDatasetSourceConfig(): Option[List[DatasetSourceConfig]] = {
    DatasetRegistryService.readAllDatasetSourceConfig()
  }

  def getDatasetSourceConfigById(datasetId: String): Option[List[DatasetSourceConfig]] = {
    DatasetRegistryService.readDatasetSourceConfig(datasetId)
  }

  def getDatasetTransformations(datasetId: String): Option[List[DatasetTransformation]] = {
    datasetTransformations.get(datasetId)
  }

  def getDatasources(datasetId: String): Option[List[DataSource]] = {
    DatasetRegistryService.readDatasources(datasetId)
  }

  def getAllDatasources(): List[DataSource] = {
    val datasourceList = DatasetRegistryService.readAllDatasources()
    datasourceList.getOrElse(List())
  }

  def getDataSetIds(): List[String] = {
    datasets.keySet.toList
  }

  def updateDatasourceRef(datasource: DataSource, datasourceRef: String): Int = {
    DatasetRegistryService.updateDatasourceRef(datasource, datasourceRef)
  }

  def updateConnectorStats(id: String, lastFetchTimestamp: Timestamp, records: Long): Int = {
    DatasetRegistryService.updateConnectorStats(id, lastFetchTimestamp, records)
  }

  def updateConnectorDisconnections(id: String, disconnections: Int): Int = {
    DatasetRegistryService.updateConnectorDisconnections(id, disconnections)
  }

  def updateConnectorAvgBatchReadTime(id: String, avgReadTime: Long): Int = {
    DatasetRegistryService.updateConnectorAvgBatchReadTime(id, avgReadTime)
  }

}