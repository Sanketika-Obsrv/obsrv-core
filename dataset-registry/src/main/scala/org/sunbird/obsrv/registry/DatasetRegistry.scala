package org.sunbird.obsrv.registry

import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset, DatasetSourceConfig, DatasetTransformation}
import org.sunbird.obsrv.service.DatasetRegistryService

import java.sql.Timestamp

object DatasetRegistry {

  private val datasets: Map[String, Dataset] = DatasetRegistryService.readAllDatasets()
  private val datasetTransformations: Map[String, List[DatasetTransformation]] = DatasetRegistryService.readAllDatasetTransformations()
  private val datasetSourceConfig: Option[List[DatasetSourceConfig]] = DatasetRegistryService.readAllDatasetSourceConfig()
  private val datasources: Map[String, List[DataSource]] = DatasetRegistryService.readAllDatasources()

  def getAllDatasets(datasetType: String): List[Dataset] = {
    datasets.filter(f => f._2.datasetType.equals(datasetType)).values.toList
  }

  def getDataset(id: String): Option[Dataset] = {
    datasets.get(id)
  }

  def getDatasetSourceConfig(): Option[List[DatasetSourceConfig]] = {
    datasetSourceConfig
  }

  def getDatasetSourceConfigById(datasetId: String): DatasetSourceConfig = {
    datasetSourceConfig.map(configList => configList.filter(_.datasetId.equalsIgnoreCase(datasetId))).get.head
  }

  def getDatasetTransformations(datasetId: String): Option[List[DatasetTransformation]] = {
    datasetTransformations.get(datasetId)
  }

  def getDatasources(datasetId: String): Option[List[DataSource]] = {
    datasources.get(datasetId)
  }

  def getDataSetIds(datasetType: String): List[String] = {
    datasets.filter(f => f._2.datasetType.equals(datasetType)).keySet.toList
  }

  def updateDatasourceRef(datasource: DataSource, datasourceRef: String): Unit = {
    DatasetRegistryService.updateDatasourceRef(datasource, datasourceRef)
  }

  def updateConnectorStats(datasetId: String, lastFetchTimestamp: Timestamp, records: Long): Unit = {
    DatasetRegistryService.updateConnectorStats(datasetId, lastFetchTimestamp, records)
  }

  def updateConnectorDisconnections(datasetId: String, disconnections: Int): Unit = {
    DatasetRegistryService.updateConnectorDisconnections(datasetId, disconnections)
  }

  def updateConnectorAvgBatchReadTime(datasetId: String, avgReadTime: Long): Unit = {
    DatasetRegistryService.updateConnectorAvgBatchReadTime(datasetId, avgReadTime)
  }

}