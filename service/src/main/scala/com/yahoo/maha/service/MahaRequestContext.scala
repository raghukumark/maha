package com.yahoo.maha.service

import com.yahoo.maha.core.bucketing.BucketParams
import com.yahoo.maha.core.request.ReportingRequest

/**
  * Created by hiral on 4/5/18.
  */
case class MahaRequestContext(registryName: String
                              , bucketParams: BucketParams
                              , reportingRequest: ReportingRequest
                              , rawJson: Array[Byte]
                              , context: Map[String, Any])
