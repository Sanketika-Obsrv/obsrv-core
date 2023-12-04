package org.sunbird.obsrv.registry

import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset, DatasetSourceConfig, DatasetTransformation}
import org.sunbird.obsrv.service.DatasetRegistryService

import java.sql.Timestamp

object DatasetRegistry {

  private val datasets: Map[String, Dataset] = DatasetRegistryService.readAllDatasets()
  private val datasetTransformations: Map[String, List[DatasetTransformation]] = DatasetRegistryService.readAllDatasetTransformations()

  def getAllDatasets(datasetType: String): List[Dataset] = {
    val datasetList = DatasetRegistryService.readAllDatasets()
    datasetList.filter(f => f._2.datasetType.equals(datasetType)).values.toList
  }

  def getDataset(id: String): Option[Dataset] = {
    datasets.get(id)
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

  def getDataSetIds(datasetType: String): List[String] = {
    datasets.filter(f => f._2.datasetType.equals(datasetType)).keySet.toList
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