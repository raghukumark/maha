// Copyright 2017, Yahoo Holdings Inc.
// Licensed under the terms of the Apache License 2.0. Please see LICENSE file in project root for terms.
package com.yahoo.maha.core.query.hive

import java.nio.charset.StandardCharsets

import com.yahoo.maha.core.CoreSchema.AdvertiserSchema
import com.yahoo.maha.core.{fact, _}
import com.yahoo.maha.core.fact.{ForceFilter, OnSelect}
import com.yahoo.maha.core.query.{QueryGeneratorRegistry, _}
import com.yahoo.maha.core.registry.Registry
import com.yahoo.maha.core.request.ReportingRequest
import org.mockito.Mockito._
import org.mockito.Matchers._

import scala.util.Try

/**
 * Created by shengyao on 12/21/15.
 */
class HiveQueryGeneratorV1Test extends BaseHiveQueryGeneratorTest {

  lazy val defaultRegistry = getDefaultRegistry()

  test("registering Hive query generation multiple times should fail") {
    intercept[IllegalArgumentException] {
      val dummyQueryGenerator = new QueryGenerator[WithHiveEngine] {
        override def generate(queryContext: QueryContext): Query = {
          null
        }

        override def engine: Engine = HiveEngine
      }

      val queryGeneratorRegistryTest = new QueryGeneratorRegistry
      queryGeneratorRegistryTest.register(HiveEngine, dummyQueryGenerator, Version.v1)
      HiveQueryGeneratorV1.register(queryGeneratorRegistryTest, DefaultPartitionColumnRenderer, TestUDFRegistrationFactory())
    }
  }

  test("test register with query generator for a different engine") {
    intercept[IllegalArgumentException] {
      val queryGeneratorRegistryTest = new QueryGeneratorRegistry
      val dummyOracleQueryGenerator = new QueryGenerator[WithOracleEngine] {
        override def generate(queryContext: QueryContext): Query = {
          null
        }

        override def engine: Engine = OracleEngine
      }
      queryGeneratorRegistryTest.register(HiveEngine, dummyOracleQueryGenerator, Version.v1)
      HiveQueryGeneratorV1.register(queryGeneratorRegistryTest, DefaultPartitionColumnRenderer, TestUDFRegistrationFactory())
    }
  }

  test("Invalid query context") {
    intercept[UnsupportedOperationException] {
      val hiveQueryGeneratorV1 = new HiveQueryGeneratorV1(DefaultPartitionColumnRenderer, TestUDFRegistrationFactory())
      val queryContext = mock(classOf[QueryContext])
      hiveQueryGeneratorV1.generate(queryContext)
    }
  }

  test("generating hive query") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)

    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))


    val resultQuery = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery]
    assert(Version.v1.equals(resultQuery.queryGenVersion.get), "Expected v1 queryGenVersion")

  }

  test("generating hive query with custom rollups") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test_custom_rollups.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)

    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))


    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

  }

  test("user stats hourly") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "user_stats_hourly.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    val sourceForceFilter: EqualityFilter = EqualityFilter("Source", "2", isForceFilter = true)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))
    assert(queryPipelineTry.toOption.get.factBestCandidate.get.filters.size == 3, requestModel.errorMessage("Building request model failed"))
    assert(queryPipelineTry.toOption.get.factBestCandidate.get.filters.contains(sourceForceFilter), requestModel.errorMessage("Building request model failed"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

  }

  test("Date type columns should be rendered with getFormattedDate udf in outer select") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_date_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("getFormattedDate"), "Date type columms should be wrapped in getFormattedDate udf")
  }

  test("Should escape string if escaping is required") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_escaping_required_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("getCsvEscapedString"), "Should escape string if escaping is required")
  }

  test("Hive query should contain UDF definitions ") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val query = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery]
    assert(query.udfStatements.nonEmpty, "Hive Query should contain udf statements")
  }

  test("should support execution parameters") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val query = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery]
  }

  test("Concatenated cols should be wrapped in CONCAT_WS()") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("CONCAT_WS"), "Concatenated cols should be wrapped in CONCAT_WS()")
  }

  test("Should mangle non-id column aliases") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_generator_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("mang_search_term"), "Should mangle non-id field: Search Term")
    assert(result.contains("mang_day"), "Should mangle non-id field: Day")
    assert(result.contains("mang_keyword"), "Should mangle non-id field: Keyword")
    assert(result.contains("mang_impression"), "Should mangle non-id field: Impression")
    assert(result.contains("mang_delivered_match_type"), "Should mangle non-id field: Delivered Match Type")

  }

  test("Should support “NA” for NULL string") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_string_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    assert(result.contains("COALESCE(outergroupby.mang_advertiser_status, \"NA\") mang_advertiser_status"), "Should support “NA” for NULL string")
    //assert(result.contains("COALESCE(a1.mang_advertiser_status, \"NA\") mang_advertiser_status"), "Should support “NA” for NULL string")
  }

  test("Query with request DecType fields that contains max and min should return query with max and min range") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_min_max_dec_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("CASE WHEN SUM(impressions) = 0 THEN 0.0 ELSE SUM(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END * impressions) / (SUM(impressions)) END) mang_average_position"), "Should support “NA” for NULL string")
  }

  test("Query with constant requested fields should have constant columns") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_constant_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("NVL(mang_source, '')"), "No constant field in concatenated columns")
    assert(result.contains("'2' mang_source"), "No constant field in outer columns")
  }

  test("Fact group by clause should only include dim columns") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_fact_n_factder_field.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("GROUP BY landing_page_url, stats_date\n"), "Group by should only include dim columns")

  }

  test("Hive multi dimensional query") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_request_with_multiple_dimension.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

  }

  test("DateTime type columns should be rendered with getDateTimeFromEpoch udf in outer select") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "ce_stats.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    assert(result.contains("getDateTimeFromEpoch"), "Date type columms should be wrapped in getFormattedDate udf")
  }

  test("test NoopRollup expression for generated query") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_nooprollup_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    val expected =
      s"""
         |SELECT CONCAT_WS(",",NVL(mang_day, ''), NVL(advertiser_id, ''), NVL(campaign_id, ''), NVL(ad_group_id, ''), NVL(keyword_id, ''), NVL(mang_keyword, ''), NVL(mang_search_term, ''), NVL(mang_delivered_match_type, ''), NVL(mang_impressions, ''), NVL(mang_average_cpc, ''))
         |FROM(
         |SELECT getFormattedDate(stats_date) mang_day, CAST(COALESCE(account_id, 0L) as STRING) advertiser_id, CAST(COALESCE(campaign_id, 0L) as STRING) campaign_id, CAST(COALESCE(ad_group_id, 0L) as STRING) ad_group_id, CAST(COALESCE(keyword_id, 0L) as STRING) keyword_id, getCsvEscapedString(CAST(NVL(keyword, '') AS STRING)) mang_keyword, COALESCE(search_term, "None") mang_search_term, CAST(COALESCE(delivered_match_type, 0L) as STRING) mang_delivered_match_type, CAST(COALESCE(impressions, 0L) as STRING) mang_impressions, CAST(ROUND(COALESCE((CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END), 0L), 10) as STRING) mang_average_cpc
         |FROM(SELECT CASE WHEN (delivered_match_type IN (1)) THEN 'Exact' WHEN (delivered_match_type IN (2)) THEN 'Broad' WHEN (delivered_match_type IN (3)) THEN 'Phrase' ELSE 'UNKNOWN' END delivered_match_type, stats_date, keyword, ad_group_id, search_term, account_id, campaign_id, keyword_id, SUM(impressions) impressions, SUM(clicks) clicks, SUM(spend) spend
         |FROM s_stats_fact
         |WHERE (account_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY CASE WHEN (delivered_match_type IN (1)) THEN 'Exact' WHEN (delivered_match_type IN (2)) THEN 'Broad' WHEN (delivered_match_type IN (3)) THEN 'Phrase' ELSE 'UNKNOWN' END, stats_date, keyword, ad_group_id, search_term, account_id, campaign_id, keyword_id
         |
          |       )
         |ssf0
         |)
         |
        """.stripMargin


    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("test joinType for non fk dim filters for generated query") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_with_non_fk_dim_filters_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    assert(requestModel.toOption.get.anyDimHasNonFKNonForceFilter == true)

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    assert(result.contains("ssf0\nJOIN ("))
  }

  test("test joinType for fact driven queries generated query") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_with_wo_non_fk_dim_filters_test.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    assert(requestModel.toOption.get.anyDimHasNonFKNonForceFilter == false)

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    assert(result.contains("LEFT OUTER JOIN"))
  }

  test("test like filter with hive query gem") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_with_dim_like_filter.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    assert(result.contains("(lower(campaign_name) LIKE lower('%yahoo%'))"))
    val expected =
      s"""
         |SELECT CONCAT_WS(",",NVL(mang_day, ''), NVL(advertiser_id, ''), NVL(campaign_id, ''), NVL(mang_campaign_name, ''), NVL(ad_group_id, ''), NVL(keyword_id, ''), NVL(mang_keyword, ''), NVL(mang_search_term, ''), NVL(mang_delivered_match_type, ''), NVL(mang_impressions, ''), NVL(mang_average_cpc_cents, ''), NVL(mang_average_cpc, ''))
         |FROM(
         |SELECT getFormattedDate(stats_date) mang_day, CAST(COALESCE(account_id, 0L) as STRING) advertiser_id, CAST(COALESCE(ssf0.campaign_id, 0L) as STRING) campaign_id, getCsvEscapedString(CAST(NVL(c1.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(ad_group_id, 0L) as STRING) ad_group_id, CAST(COALESCE(keyword_id, 0L) as STRING) keyword_id, getCsvEscapedString(CAST(NVL(keyword, '') AS STRING)) mang_keyword, COALESCE(search_term, "None") mang_search_term, CAST(COALESCE(delivered_match_type, 0L) as STRING) mang_delivered_match_type, CAST(COALESCE(impressions, 0L) as STRING) mang_impressions, CAST(ROUND(COALESCE((mang_average_cpc * 100), 0L), 10) as STRING) mang_average_cpc_cents, CAST(ROUND(COALESCE(mang_average_cpc, 0L), 10) as STRING) mang_average_cpc
         |FROM(SELECT CASE WHEN (delivered_match_type IN (1)) THEN 'Exact' WHEN (delivered_match_type IN (2)) THEN 'Broad' WHEN (delivered_match_type IN (3)) THEN 'Phrase' ELSE 'UNKNOWN' END delivered_match_type, stats_date, keyword, ad_group_id, search_term, account_id, campaign_id, keyword_id, SUM(impressions) impressions, (CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END) mang_average_cpc
         |FROM s_stats_fact
         |WHERE (account_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY CASE WHEN (delivered_match_type IN (1)) THEN 'Exact' WHEN (delivered_match_type IN (2)) THEN 'Broad' WHEN (delivered_match_type IN (3)) THEN 'Phrase' ELSE 'UNKNOWN' END, stats_date, keyword, ad_group_id, search_term, account_id, campaign_id, keyword_id
         |
 |       )
         |ssf0
         |JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |""".stripMargin

    whiteSpaceNormalised.normalized(result) should startWith(whiteSpaceNormalised.normalized(expected))
  }

  test("test like filter with hive query injection testing") {
    val jsonString = scala.io.Source.fromFile(getBaseDir + "hive_query_with_dim_like_filter_injection_testing.json")
      .getLines().mkString.replace("{from_date}", fromDate).replace("{to_date}", toDate)
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    assert(result.contains("(lower(campaign_name) LIKE lower('%Server Log Avoidance\t #alert(1) #alert(1) # alert(1) Shortest PoC\t $ while:; do echo \"alert(1)\" | nc -lp80; done%')"))
  }

  test("Missing group by fact cols") {
    val jsonString =
      s"""
         |{
         |"cube": "bid_reco",
         |"selectFields": [
         |{ "field": "Advertiser ID" }
         |,
         |{ "field": "Ad Group ID" }
         |,
         |{ "field": "Campaign ID" }
         |,
         |{ "field": "Bid Strategy" }
         |,
         |{ "field": "Current Base Bid" }
         |,
         |{ "field": "Modified Bid" }
         |,
         |{ "field": "Bid Modifier" }
         |,
         |{ "field": "Recommended Bid" }
         |,
         |{ "field": "Clicks" }
         |,
         |{ "field": "Forecasted Clicks" }
         |,
         |{ "field": "Impressions" }
         |,
         |{ "field": "Forecasted Impressions" }
         |,
         |{ "field": "Budget" }
         |,
         |{ "field": "Spend" }
         |,
         |{ "field": "Forecasted Spend" }
         |],
         |"filterExpressions": [
         |{ "field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate" }
         |,
         |{ "field": "Advertiser ID", "operator": "=", "value": "12345" }
         |]
         |}
      """.stripMargin
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString


    assert(result.contains("GROUP BY modified_bid, CASE WHEN (bid_strategy IN (1)) THEN 'Max Click' WHEN (bid_strategy IN (2)) THEN 'Inflection Point' ELSE 'NONE' END, ad_group_id, account_id, campaign_id, current_bid, (modified_bid - current_bid) / current_bid * 100"))
  }

  test("HiveQueryGeneratorV1Test: should fail to generate Hive query for Outer Filters") {
    val jsonString =
      s"""
         |{
         |"cube": "bid_reco",
         |"selectFields": [
         |{ "field": "Advertiser ID" }
         |,
         |{ "field": "Ad Group ID" }
         |,
         |{ "field": "Campaign ID" }
         |,
         |{ "field": "Bid Strategy" }
         |,
         |{ "field": "Current Base Bid" }
         |,
         |{ "field": "Modified Bid" }
         |,
         |{ "field": "Bid Modifier" }
         |,
         |{ "field": "Recommended Bid" }
         |,
         |{ "field": "Clicks" }
         |,
         |{ "field": "Forecasted Clicks" }
         |,
         |{ "field": "Impressions" }
         |,
         |{ "field": "Forecasted Impressions" }
         |,
         |{ "field": "Budget" }
         |,
         |{ "field": "Spend" }
         |,
         |{ "field": "Forecasted Spend" }
         |],
         |"filterExpressions": [
         |                     {"operator": "outer", "outerFilters": [
         |                          {"field": "Ad Group ID", "operator": "isnull"}
         |                          ]
         |                     },
         |{ "field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate" }
         |,
         |{ "field": "Advertiser ID", "operator": "=", "value": "12345" }
         |]
         |}
      """.stripMargin
    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isFailure, queryPipelineTry.errorMessage("Outer Filters on Hive Query should fail to get the query pipeline"))
    assert(queryPipelineTry.failed.get.getMessage.contains("Failed to find best candidate"))
  }

  test("verify dim query can generate inner select and group by with static mapping") {
    val jsonString =
      s"""{
                          "cube": "s_stats",
                          "selectFields": [
                              {"field": "Device ID"},
                              {"field": "Advertiser ID"},
                              {"field": "Impressions"},
                              {"field": "Pricing Type"},
                              {"field": "Network ID"}
                          ],
                          "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                          ]
                          }"""

    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))


    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, "dim fact sync dimension driven query with requested fields in multiple dimensions should not fail")
    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    val expected =
      s"""
         |SELECT CONCAT_WS(",",NVL(device_id, ''), NVL(advertiser_id, ''), NVL(mang_impressions, ''), NVL(mang_pricing_type, ''), NVL(network_id, ''))
         |FROM(
         |SELECT CAST(COALESCE(device_id, 0L) as STRING) device_id, CAST(COALESCE(account_id, 0L) as STRING) advertiser_id, CAST(COALESCE(impressions, 0L) as STRING) mang_impressions, CAST(COALESCE(price_type, 0L) as STRING) mang_pricing_type, COALESCE(network_type, "NA") network_id
         |FROM(SELECT decodeUDF(network_type, 'TEST_PUBLISHER', 'Test Publisher', 'CONTENT_S', 'Content Secured', 'EXTERNAL', 'External Partners', 'INTERNAL', 'Internal Properties', 'NONE') network_type, CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END price_type, account_id, CASE WHEN (device_id IN (11)) THEN 'Desktop' WHEN (device_id IN (22)) THEN 'Tablet' WHEN (device_id IN (33)) THEN 'SmartPhone' WHEN (device_id IN (-1)) THEN 'UNKNOWN' ELSE 'UNKNOWN' END device_id, SUM(impressions) impressions
         |FROM s_stats_fact
         |WHERE (account_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY decodeUDF(network_type, 'TEST_PUBLISHER', 'Test Publisher', 'CONTENT_S', 'Content Secured', 'EXTERNAL', 'External Partners', 'INTERNAL', 'Internal Properties', 'NONE'), CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END, account_id, CASE WHEN (device_id IN (11)) THEN 'Desktop' WHEN (device_id IN (22)) THEN 'Tablet' WHEN (device_id IN (33)) THEN 'SmartPhone' WHEN (device_id IN (-1)) THEN 'UNKNOWN' ELSE 'UNKNOWN' END
         |
         |       )
         |ssf0
         |)
      """.stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Query containing fields with MAX rollup should generate successfully") {
    val jsonString =
      s"""{
                          "cube": "s_stats",
                          "selectFields": [
                              {"field": "Device ID"},
                              {"field": "Campaign Name"},
                              {"field": "Impressions"},
                              {"field": "Pricing Type"},
                              {"field": "Max Price Type"}
                          ],
                          "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                          ]
                          }"""

    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, "Querypipeline containing fields with MAX rollup should generate successfully")
  }

  test("where clause: ensure duplicate filter mappings are not propagated into the where clause") {
    //currently needs to remove duplicate filter entries, as resolved in base column level
    val jsonString =
      s"""{
                          "cube": "s_stats",
                          "selectFields": [
                              {"field": "Device ID"},
                              {"field": "Advertiser ID"},
                              {"field": "Impressions"},
                              {"field": "Pricing Type"},
                              {"field": "Network ID"}
                          ],
                          "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"},
                              {"field": "Source", "operator": "=", "value": "1"},
                              {"field": "Source Name", "operator": "=", "value": "2"}
                          ]
                          }"""

    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = defaultRegistry
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))


    val queryPipelineTry = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, "dim fact sync dimension driven query with requested fields in multiple dimensions should not fail")
    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString

    val expected =
      s"""
         |SELECT CONCAT_WS(",",NVL(device_id, ''), NVL(advertiser_id, ''), NVL(mang_impressions, ''), NVL(mang_pricing_type, ''), NVL(network_id, ''))
         |FROM(
         |SELECT CAST(COALESCE(device_id, 0L) as STRING) device_id, CAST(COALESCE(account_id, 0L) as STRING) advertiser_id, CAST(COALESCE(impressions, 0L) as STRING) mang_impressions, CAST(COALESCE(price_type, 0L) as STRING) mang_pricing_type, COALESCE(network_type, "NA") network_id
         |FROM(SELECT CASE WHEN (device_id IN (11)) THEN 'Desktop' WHEN (device_id IN (22)) THEN 'Tablet' WHEN (device_id IN (33)) THEN 'SmartPhone' WHEN (device_id IN (-1)) THEN 'UNKNOWN' ELSE 'UNKNOWN' END device_id, decodeUDF(network_type, 'TEST_PUBLISHER', 'Test Publisher', 'CONTENT_S', 'Content Secured', 'EXTERNAL', 'External Partners', 'INTERNAL', 'Internal Properties', 'NONE') network_type, CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END price_type, account_id, SUM(impressions) impressions
         |FROM s_stats_fact
         |WHERE (account_id = 12345) AND (stats_source = 2) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY CASE WHEN (device_id IN (11)) THEN 'Desktop' WHEN (device_id IN (22)) THEN 'Tablet' WHEN (device_id IN (33)) THEN 'SmartPhone' WHEN (device_id IN (-1)) THEN 'UNKNOWN' ELSE 'UNKNOWN' END, decodeUDF(network_type, 'TEST_PUBLISHER', 'Test Publisher', 'CONTENT_S', 'Content Secured', 'EXTERNAL', 'External Partners', 'INTERNAL', 'Internal Properties', 'NONE'), CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END, account_id
         |
         |       )
         |ssf0
         |)
      """.stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("fact only query context should be switched to CombinedQueryContext") {
    val hiveQueryGeneratorV1 = spy(new HiveQueryGeneratorV1(DefaultPartitionColumnRenderer, TestUDFRegistrationFactory()))
    val queryContext = mock(classOf[FactQueryContext])
    try {
      hiveQueryGeneratorV1.generate(queryContext)
    } catch {
      case e: Exception => // Ignore
    }

    verify(hiveQueryGeneratorV1).generateQuery(any(classOf[CombinedQueryContext]))
  }

  test("Duplicate registration of the generator") {
    val failRegistry = new QueryGeneratorRegistry
    val dummyHiveQueryGenerator = new QueryGenerator[WithHiveEngine] {
      override def generate(queryContext: QueryContext): Query = {
        null
      }

      override def engine: Engine = OracleEngine
    }
    val dummyFalseQueryGenerator = new QueryGenerator[WithDruidEngine] {
      override def generate(queryContext: QueryContext): Query = {
        null
      }

      override def engine: Engine = DruidEngine
    }
    failRegistry.register(OracleEngine, dummyHiveQueryGenerator)
    failRegistry.register(DruidEngine, dummyFalseQueryGenerator)

    HiveQueryGeneratorV1.register(failRegistry, DefaultPartitionColumnRenderer, TestUDFRegistrationFactory())
  }


  // Outer Group By
  test("Successfully generated Outer Group By Query with dim non id field and fact field") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin

    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,SUM(spend) spend
         |FROM(SELECT campaign_id, SUM(spend) spend
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id
         |
    |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
    |GROUP BY c1.mang_campaign_name) outergroupby
         |)""".stripMargin
    println(result)
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query with dim non id field and derived fact field having dim source col") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Source"
                             },
                             {
                               "field": "N Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin

    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_source, ''), NVL(mang_n_spend, ''))
    FROM(
    SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(stats_source, 0L) as STRING) mang_source, CAST(ROUND(COALESCE(decodeUDF(stats_source, 1, spend, 0.0), 0L), 10) as STRING) mang_n_spend
    FROM(
    SELECT c1.mang_campaign_name,af0.stats_source,SUM(spend) spend
    FROM(SELECT campaign_id, stats_source, SUM(spend) spend
    FROM ad_fact1
    WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
    GROUP BY campaign_id, stats_source

           )
    af0
    LEFT OUTER JOIN (
    SELECT campaign_name AS mang_campaign_name, id c1_id
    FROM campaing_hive
    WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
    )
    c1
    ON
    af0.campaign_id = c1.c1_id

    GROUP BY c1.mang_campaign_name,af0.stats_source) outergroupby
    )""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated timeseries Outer Group By Query with dim non id field and fact field") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Day",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_day, ''), NVL(mang_campaign_name, ''), NVL(mang_spend, ''))
      FROM(
      SELECT getFormattedDate(stats_date) mang_day, getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
      FROM(
      SELECT af0.stats_date,c1.mang_campaign_name,SUM(spend) spend
      FROM(SELECT campaign_id, stats_date, SUM(spend) spend
      FROM ad_fact1
      WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
      GROUP BY campaign_id, stats_date

             )
      af0
      LEFT OUTER JOIN (
      SELECT campaign_name AS mang_campaign_name, id c1_id
      FROM campaing_hive
      WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
      )
      c1
      ON
      af0.campaign_id = c1.c1_id

      GROUP BY af0.stats_date,c1.mang_campaign_name) outergroupby
      )""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query with 2 dimension non id fields") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Advertiser Currency",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_advertiser_currency, ''), NVL(mang_spend, ''))
      FROM(
      SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, COALESCE(outergroupby.mang_advertiser_currency, "NA") mang_advertiser_currency, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
      FROM(
      SELECT c2.mang_campaign_name,a1.mang_advertiser_currency,SUM(spend) spend
      FROM(SELECT advertiser_id, campaign_id, SUM(spend) spend
      FROM ad_fact1
      WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
      GROUP BY advertiser_id, campaign_id

             )
      af0
      LEFT OUTER JOIN (
      SELECT currency AS mang_advertiser_currency, id a1_id
      FROM advertiser_hive
      WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (id = 12345)
      )
      a1
      ON
      af0.advertiser_id = a1.a1_id
             LEFT OUTER JOIN (
      SELECT advertiser_id AS advertiser_id, campaign_name AS mang_campaign_name, id c2_id
      FROM campaing_hive
      WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
      )
      c2
      ON
      af0.campaign_id = c2.c2_id

      GROUP BY c2.mang_campaign_name,a1.mang_advertiser_currency) outergroupby
      )""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Should not generate Outer Group By Query context with 2 dimension non id fields and one fact higher level ID field than best dims") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Advertiser Currency",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Ad Group ID",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    assert(!result.contains("outergroupby"))
  }

  test("Successfully generated Outer Group By Query with 2 dimension non id fields and and two fact transitively dependent cols") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Advertiser Currency",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Average CPC Cents",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Average CPC",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_advertiser_currency, ''), NVL(mang_average_cpc_cents, ''), NVL(mang_average_cpc, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, COALESCE(outergroupby.mang_advertiser_currency, "NA") mang_advertiser_currency, CAST(ROUND(COALESCE((CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END) * 100, 0L), 10) as STRING) mang_average_cpc_cents, CAST(ROUND(COALESCE(CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END, 0L), 10) as STRING) mang_average_cpc, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c2.mang_campaign_name,a1.mang_advertiser_currency,SUM(clicks) clicks,SUM(spend) spend
         |FROM(SELECT advertiser_id, campaign_id, SUM(spend) spend, SUM(clicks) clicks
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY advertiser_id, campaign_id
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT currency AS mang_advertiser_currency, id a1_id
         |FROM advertiser_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (id = 12345)
         |)
         |a1
         |ON
         |af0.advertiser_id = a1.a1_id
         |      LEFT OUTER JOIN (
         |SELECT advertiser_id AS advertiser_id, campaign_name AS mang_campaign_name, id c2_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c2
         |ON
         |af0.campaign_id = c2.c2_id
         |
      |GROUP BY c2.mang_campaign_name,a1.mang_advertiser_currency) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if fk col one level less than Highest dim candidate level is requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Ad Status",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Campaign ID",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_ad_status, ''), NVL(mang_campaign_name, ''), NVL(campaign_id, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT COALESCE(outergroupby.mang_ad_status, "NA") mang_ad_status, getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(campaign_id, 0L) as STRING) campaign_id, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id,SUM(spend) spend
         |FROM(SELECT campaign_id, ad_id, SUM(spend) spend
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id, ad_id
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |      LEFT OUTER JOIN (
         |SELECT campaign_id AS campaign_id, decodeUDF(status, 'ON', 'ON', 'OFF') AS mang_ad_status, id a2_id
         |FROM ad_dim_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |a2
         |ON
         |af0.ad_id = a2.a2_id
         |
      |GROUP BY a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if CustomRollup col is requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Average CPC",
                               "alias": null,
                               "value": null
                             },
                             {
                              "field": "Spend",
                              "alias": null,
                              "value": null
                              }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_average_cpc, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(ROUND(COALESCE(CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END, 0L), 10) as STRING) mang_average_cpc, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,SUM(clicks) clicks,SUM(spend) spend
         |FROM(SELECT campaign_id, SUM(spend) spend, SUM(clicks) clicks
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
      |GROUP BY c1.mang_campaign_name) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if CustomRollup col with Derived Expression having rollups is requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Average Position",
                               "alias": null,
                               "value": null
                             },
                             {
                              "field": "Spend",
                              "alias": null,
                              "value": null
                              }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin

    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_average_position, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(ROUND(COALESCE(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END, 0.0), 10) as STRING) mang_average_position, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,(CASE WHEN SUM(impressions) = 0 THEN 0.0 ELSE SUM(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END * impressions) / (SUM(impressions)) END) avg_pos,SUM(spend) spend
         |FROM(SELECT campaign_id, SUM(spend) spend, SUM(impressions) impressions, (CASE WHEN SUM(impressions) = 0 THEN 0.0 ELSE SUM(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END * impressions) / (SUM(impressions)) END) avg_pos
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
      |GROUP BY c1.mang_campaign_name) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if OracleCustomRollup col with Derived Expression having CustomRollup and DerCol are requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Average Position",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Average CPC"
                             },
                             {
                              "field": "Spend",
                              "alias": null,
                              "value": null
                              }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_average_position, ''), NVL(mang_average_cpc, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(ROUND(COALESCE(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END, 0.0), 10) as STRING) mang_average_position, CAST(ROUND(COALESCE(CASE WHEN clicks = 0 THEN 0.0 ELSE spend / clicks END, 0L), 10) as STRING) mang_average_cpc, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,(CASE WHEN SUM(impressions) = 0 THEN 0.0 ELSE SUM(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END * impressions) / (SUM(impressions)) END) avg_pos,SUM(clicks) clicks,SUM(spend) spend
         |FROM(SELECT campaign_id, SUM(spend) spend, SUM(impressions) impressions, (CASE WHEN SUM(impressions) = 0 THEN 0.0 ELSE SUM(CASE WHEN ((avg_pos >= 0.1) AND (avg_pos <= 500)) THEN avg_pos ELSE 0.0 END * impressions) / (SUM(impressions)) END) avg_pos, SUM(clicks) clicks
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id
         |
      |)
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
      |GROUP BY c1.mang_campaign_name) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if column is derived from dim column") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Advertiser ID",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "N Average CPC"
                             },
                             {
                              "field": "Spend",
                              "alias": null,
                              "value": null
                              }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(advertiser_id, ''), NVL(mang_n_average_cpc, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(advertiser_id, 0L) as STRING) advertiser_id, CAST(ROUND(COALESCE(CASE WHEN decodeUDF(stats_source, 1, clicks, 0.0) = 0 THEN 0.0 ELSE decodeUDF(stats_source, 1, spend, 0.0) / decodeUDF(stats_source, 1, clicks, 0.0) END, 0L), 10) as STRING) mang_n_average_cpc, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,af0.advertiser_id,af0.stats_source,SUM(clicks) clicks,SUM(spend) spend
         |FROM(SELECT advertiser_id, campaign_id, SUM(spend) spend, stats_source, SUM(clicks) clicks
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY advertiser_id, campaign_id, stats_source
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
      |GROUP BY c1.mang_campaign_name,af0.advertiser_id,af0.stats_source) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if NoopRollupp column requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Impression Share",
                               "alias": null,
                               "value": null
                             },
                             {
                              "field": "Spend",
                              "alias": null,
                              "value": null
                              }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_campaign_name, ''), NVL(mang_impression_share, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(impression_share, 0L) as STRING) mang_impression_share, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(
         |SELECT c1.mang_campaign_name,(decodeUDF(MAX(show_flag), 1, ROUND(CASE WHEN SUM(s_impressions) = 0 THEN 0.0 ELSE SUM(impressions) / (SUM(s_impressions)) END, 4), NULL)) impression_share,SUM(spend) spend
         |FROM(SELECT campaign_id, SUM(spend) spend, show_flag, SUM(s_impressions) s_impressions, SUM(impressions) impressions
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY campaign_id, show_flag
         |
      |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |
      |GROUP BY c1.mang_campaign_name) outergroupby
         |)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated Outer Group By Query if NoopRollupp derived column is requested for non-derived source fields") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Ad Status",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Campaign ID",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             },
                             {
                                "field": "Engagement Rate",
                                "alias": null,
                                "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_ad_status, ''), NVL(mang_campaign_name, ''), NVL(campaign_id, ''), NVL(mang_spend, ''), NVL(mang_engagement_rate, ''))
FROM(
SELECT COALESCE(outergroupby.mang_ad_status, "NA") mang_ad_status, getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(campaign_id, 0L) as STRING) campaign_id, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend, CAST(ROUND(COALESCE(100 * mathUDF(engagement_count, impressions), 0L), 10) as STRING) mang_engagement_rate
FROM(
SELECT a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id,SUM(spend) spend,SUM(engagement_count) engagement_count,SUM(impressions) impressions
FROM(SELECT ad_id, campaign_id, SUM(spend) spend, SUM(engagement_count) engagement_count, SUM(impressions) impressions
FROM ad_fact1
WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
GROUP BY ad_id, campaign_id

       )
af0
LEFT OUTER JOIN (
SELECT campaign_name AS mang_campaign_name, id c1_id
FROM campaing_hive
WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
)
c1
ON
af0.campaign_id = c1.c1_id
       LEFT OUTER JOIN (
SELECT campaign_id AS campaign_id, decodeUDF(status, 'ON', 'ON', 'OFF') AS mang_ad_status, id a2_id
FROM ad_dim_hive
WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
)
a2
ON
af0.ad_id = a2.a2_id

GROUP BY a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id) outergroupby
)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }


  test("Successfully generated Outer Group By Query if aggregate derived column (eg UDAF) is requested") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             {
                               "field": "Ad Status",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Campaign Name"
                             },
                             {
                               "field": "Campaign ID",
                               "alias": null,
                               "value": null
                             },
                             {
                               "field": "Spend",
                               "alias": null,
                               "value": null
                             },
                             {
                                "field": "Engagement Rate",
                                "alias": null,
                                "value": null
                             },
                             {
                                "field": "Paid Engagement Rate",
                                "alias": null,
                                "value": null
                             }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val result = generateHiveQuery(jsonString)
    val expected =
      s"""SELECT CONCAT_WS(",",NVL(mang_ad_status, ''), NVL(mang_campaign_name, ''), NVL(campaign_id, ''), NVL(mang_spend, ''), NVL(mang_engagement_rate, ''), NVL(mang_paid_engagement_rate, ''))
FROM(
SELECT COALESCE(outergroupby.mang_ad_status, "NA") mang_ad_status, getCsvEscapedString(CAST(NVL(outergroupby.mang_campaign_name, '') AS STRING)) mang_campaign_name, CAST(COALESCE(campaign_id, 0L) as STRING) campaign_id, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend, CAST(ROUND(COALESCE(100 * mathUDF(engagement_count, impressions), 0L), 10) as STRING) mang_engagement_rate, CAST(ROUND(COALESCE(mang_paid_engagement_rate, 0L), 10) as STRING) mang_paid_engagement_rate
FROM(
SELECT a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id,SUM(spend) spend,SUM(engagement_count) engagement_count,SUM(impressions) impressions,(100 * mathUDAF(engagement_count, 0, 0, clicks, impressions)) mang_paid_engagement_rate
FROM(SELECT ad_id, campaign_id, SUM(spend) spend, SUM(engagement_count) engagement_count, SUM(clicks) clicks, SUM(impressions) impressions
FROM ad_fact1
WHERE (advertiser_id = 12345) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
GROUP BY ad_id, campaign_id

       )
af0
LEFT OUTER JOIN (
SELECT campaign_name AS mang_campaign_name, id c1_id
FROM campaing_hive
WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
)
c1
ON
af0.campaign_id = c1.c1_id
       LEFT OUTER JOIN (
SELECT campaign_id AS campaign_id, decodeUDF(status, 'ON', 'ON', 'OFF') AS mang_ad_status, id a2_id
FROM ad_dim_hive
WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
)
a2
ON
af0.ad_id = a2.a2_id

GROUP BY a2.mang_ad_status,c1.mang_campaign_name,af0.campaign_id) outergroupby
)""".stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  test("Successfully generated conditional Filters override") {
    val jsonString =
      s"""{
                           "cube": "performance_stats",
                           "selectFields": [
                             { "field": "Advertiser ID" },
                             { "field": "Campaign ID" },
                             { "field": "Campaign Name" },
                             { "field": "Day" },
                             { "field": "Pricing Type" },
                             { "field": "Impressions" },
                             { "field": "Clicks" },
                             { "field": "CTR" },
                             { "field": "Impression Share" },
                             { "field": "Conversion Assists" },
                             { "field": "Spend" }
                           ],
                           "filterExpressions": [
                              {"field": "Advertiser ID", "operator": "=", "value": "12345"},
                              {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"}
                           ]
                           }""".stripMargin
    val conditionalFields: Set[String] = Set("Conversion Assists")
    val conditionalForceFilters: Set[ForceFilter] = Set(ForceFilter(InFilter("Flag", List("1", "2"), isForceFilter = true), filterCondition = Some(OnSelect(conditionalFields))))
    val forceFilters: Set[ForceFilter] = Set(ForceFilter(EqualityFilter("Flag", "1", isForceFilter = true, isOverridable = true), isForceFilterOverridable = true))
    val result = generateHiveQuery(jsonString, forceFilters)
    val expected =
      s"""
         |SELECT CONCAT_WS(",",NVL(advertiser_id, ''), NVL(campaign_id, ''), NVL(mang_campaign_name, ''), NVL(mang_day, ''), NVL(mang_pricing_type, ''), NVL(mang_impressions, ''), NVL(mang_clicks, ''), NVL(mang_ctr, ''), NVL(mang_impression_share, ''), NVL(mang_conversion_assists, ''), NVL(mang_spend, ''))
         |FROM(
         |SELECT CAST(COALESCE(advertiser_id, 0L) as STRING) advertiser_id, CAST(COALESCE(af0.campaign_id, 0L) as STRING) campaign_id, getCsvEscapedString(CAST(NVL(c1.mang_campaign_name, '') AS STRING)) mang_campaign_name, getFormattedDate(stats_date) mang_day, CAST(COALESCE(price_type, 0L) as STRING) mang_pricing_type, CAST(COALESCE(impressions, 0L) as STRING) mang_impressions, CAST(COALESCE(mang_clicks, 0L) as STRING) mang_clicks, CAST(ROUND(COALESCE(CTR, 0L), 10) as STRING) mang_ctr, CAST(COALESCE(mang_impression_share, 0L) as STRING) mang_impression_share, CAST(COALESCE(mang_conversion_assists, 0L) as STRING) mang_conversion_assists, CAST(ROUND(COALESCE(spend, 0.0), 10) as STRING) mang_spend
         |FROM(SELECT stats_date, CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END price_type, advertiser_id, campaign_id, SUM(impressions) impressions, SUM(spend) spend, SUM(clicks) mang_clicks, (SUM(CASE WHEN impressions = 0 THEN 0.0 ELSE clicks / impressions END)) CTR, SUM(decodeUDF(coalesce(show_flag, 1), 2, {mta_count, 0)) mang_conversion_assists, (ROUND((decodeUDF(MAX(show_flag), 1, ROUND(CASE WHEN SUM(s_impressions) = 0 THEN 0.0 ELSE SUM(impressions) / (SUM(s_impressions)) END, 4), NULL)), 5)) mang_impression_share
         |FROM ad_fact1
         |WHERE (advertiser_id = 12345) AND (coalesce(show_flag, 1) = 1) AND (stats_date >= '$fromDate' AND stats_date <= '$toDate')
         |GROUP BY stats_date, CASE WHEN (price_type IN (1)) THEN 'CPC' WHEN (price_type IN (6)) THEN 'CPV' WHEN (price_type IN (2)) THEN 'CPA' WHEN (price_type IN (-10)) THEN 'CPE' WHEN (price_type IN (-20)) THEN 'CPF' WHEN (price_type IN (7)) THEN 'CPCV' WHEN (price_type IN (3)) THEN 'CPM' ELSE 'NONE' END, advertiser_id, campaign_id
         |
         |       )
         |af0
         |LEFT OUTER JOIN (
         |SELECT campaign_name AS mang_campaign_name, id c1_id
         |FROM campaing_hive
         |WHERE ((load_time = '%DEFAULT_DIM_PARTITION_PREDICTATE%' ) AND (shard = 'all' )) AND (advertiser_id = 12345)
         |)
         |c1
         |ON
         |af0.campaign_id = c1.c1_id
         |       )
         """.stripMargin
    result should equal(expected)(after being whiteSpaceNormalised)
  }

  def generateHiveQuery(requestJson: String, forceFilters: Set[ForceFilter] = Set.empty): String = {
    val requestRaw = ReportingRequest.deserializeAsync(requestJson.getBytes(StandardCharsets.UTF_8), AdvertiserSchema)
    val registry = getDefaultRegistry(forceFilters = forceFilters)
    generateHiveQuery(registry, requestRaw.toOption.get)
  }

  def generateHiveQuery(registry: Registry, requestRaw : ReportingRequest ): String = {
    val request = ReportingRequest.forceHive(requestRaw)
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))

    val queryPipelineTry: Try[QueryPipeline] = generatePipelineForQgenVersion(registry, requestModel.toOption.get, Version.v1)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.failed.errorMessage("Fail to get the query pipeline"))

    val result = queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[HiveQuery].asString
    println(result)
    result
  }

}