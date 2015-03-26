/*
 * Copyright (C) 2014 Dell, Inc.
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

package com.dell.doradus.olap.search;

import com.dell.doradus.common.TableDefinition;
import com.dell.doradus.olap.store.CubeSearcher;
import com.dell.doradus.search.FieldSet;
import com.dell.doradus.search.SearchResultList;
import com.dell.doradus.search.aggregate.SortOrder;
import com.dell.doradus.search.query.Query;

public class Searcher {
	
	
	public static SearchResultList search(CubeSearcher searcher, TableDefinition tableDef, Query query, FieldSet fieldSet, int size, SortOrder[] sortOrders) {
    	Result documents = ResultBuilder.search(tableDef, query, searcher);
		SearchResultList list = SearchResultBuilder.build(searcher, documents, fieldSet, size, sortOrders);
		return list;
	}
	
}
