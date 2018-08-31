// Copyright 2017, Yahoo Holdings Inc.
// Licensed under the terms of the Apache License 2.0. Please see LICENSE file in project root for terms.
package com.yahoo.maha.core

import com.yahoo.maha.core.ddl.DDLAnnotation
import com.yahoo.maha.core.fact.ForceFilter

/**
 * Created by hiral on 11/6/15.
 */
trait BaseTable {
  def name: String
  def engine: Engine
  def columnsByNameMap: Map[String, Column]
  def schemas: Set[Schema]
  def ddlAnnotation: Option[DDLAnnotation]
}

trait PublicTable {
  def name: String
  def forceFilters: Set[ForceFilter]
  def forcedFiltersByAliasMap: Map[String, ForcedFilter]
  def allColumnsByAlias: Set[String]
  def columnsByAliasMap: Map[String, PublicColumn]
  def requiredAliases : Set[String]
  def dependentColumns: Set[String]
  def incompatibleColumns: Map[String, Set[String]]
  def restrictedSchemasMap: Map[String, Set[Schema]]
  def requiredFilterAliases : Set[String]
  def validateForcedFilters() : Unit = {
    forceFilters.foreach { forcedFilter =>
        require(allColumnsByAlias(forcedFilter.filter.field), s"Forced filter on non-existing column : $forcedFilter")
        require(forcedFilter.filter.isForceFilter, s"Forced Filter boolean ${forcedFilter.filter.isForceFilter}, expected true")
    }
  }
}
