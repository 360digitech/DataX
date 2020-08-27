package com.alibaba.datax.plugin.reader.elasticsearchreader;


import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.spi.Reader;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.slice.SliceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ESReader extends Reader {
    private static final Logger LOG = LoggerFactory
            .getLogger(ESReader.class);


    public static class Job extends Reader.Job {


        private String esClusterName = null;

        private List esClusterAddress = null;

        private String esUsername = null;
        private String esPassword = null;

        private String esIndex = null;
        private JSONArray esColumnMeta = null;
        private String esType = null;


        private String query = null;

//        private TransportClient client = null;

        private RestHighLevelClient esClient = null;


        private Configuration readerSplitConfiguration = null;

        @Override
        public void preCheck() {
            super.preCheck();
        }

        @Override
        public void preHandler(Configuration jobConfiguration) {
            super.preHandler(jobConfiguration);
        }

        @Override
        public void init() {
            this.readerSplitConfiguration = super.getPluginJobConf();


            this.esColumnMeta = JSON.parseArray(this.readerSplitConfiguration.getString(Key.esColumn));
            this.esClusterAddress = this.readerSplitConfiguration.getList(Key.esClusterAddress);
            this.esIndex = this.readerSplitConfiguration.getString(Key.esIndex);
            this.esType = this.readerSplitConfiguration.getString(Key.esType);
            this.esUsername = this.readerSplitConfiguration.getString(Key.esUsername);
            this.esPassword = this.readerSplitConfiguration.getString(Key.esPassword);
            this.query = this.readerSplitConfiguration.getString(Key.query);
        }

        @Override
        public void prepare() {
            super.prepare();

            this.esClient = EsUtil.getClient(this.esClusterAddress, this.esUsername, this.esPassword, this.readerSplitConfiguration);
        }

        @Override
        public void post() {
            super.post();
        }

        @Override
        public void postHandler(Configuration jobConfiguration) {
            super.postHandler(jobConfiguration);
        }

        @Override
        public void destroy() {
            try {
                this.esClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public List<Configuration> split(int adviceNumber) {
            SearchRequest searchRequest = new SearchRequest(this.esIndex);
            if (StringUtils.isNotBlank(this.esType)) {
                searchRequest.types(this.esType);
            }
            try {
                SearchResponse searchResponse = this.esClient.search(searchRequest);
                int shards = searchResponse.getSuccessfulShards();
                if (adviceNumber > shards) {
                    LOG.info("期望的channel数为:{},超过了最大shard数：{},channel数重置为shard数:{}", adviceNumber, shards, shards);
                    adviceNumber = shards;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


            List<Configuration> readerSplitConfigurations = new ArrayList<Configuration>();
            for (int i = 0; i < adviceNumber; i++) {
                Configuration readerSplitConfiguration = this.readerSplitConfiguration.clone();
                readerSplitConfiguration.set(Key.channelIndex, i);
                readerSplitConfiguration.set(Key.channelNum, adviceNumber);
                readerSplitConfigurations.add(readerSplitConfiguration);
            }
            return readerSplitConfigurations;
        }

    }

    public static class Task extends Reader.Task {

        private Configuration readerSplitConfiguration = null;

        private String esClusterName = null;


        private List esClusterAddress = null;
        private String esUsername = null;
        private String esPassword = null;

        private String esIndex = null;
        private JSONArray esColumnMeta = null;
        private String esType = null;


        private String query = null;

//        private TransportClient client = null;

        private Integer batchSize = 0;
        private RestHighLevelClient esClient = null;
        private long keepAlive = 1;
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        @Override
        public void preCheck() {
            super.preCheck();
        }

        @Override
        public void preHandler(Configuration jobConfiguration) {
            super.preHandler(jobConfiguration);
        }

        @Override
        public void init() {
            this.readerSplitConfiguration = super.getPluginJobConf();


            this.esColumnMeta = JSON.parseArray(this.readerSplitConfiguration.getString(Key.esColumn));
            this.esClusterAddress = this.readerSplitConfiguration.getList(Key.esClusterAddress);
            this.esIndex = this.readerSplitConfiguration.getString(Key.esIndex);
            this.esType = this.readerSplitConfiguration.getString(Key.esType);
            this.batchSize = this.readerSplitConfiguration.getInt(Key.batchSize, 1000);
            this.esUsername = this.readerSplitConfiguration.getString(Key.esUsername);
            this.esPassword = this.readerSplitConfiguration.getString(Key.esPassword);
            this.keepAlive = this.readerSplitConfiguration.getLong(Key.keepAlive, 1);


            this.query = this.readerSplitConfiguration.getString(Key.query);
        }

        @Override
        public void prepare() {
            super.prepare();
            this.esClient = EsUtil.getClient(this.esClusterAddress, this.esUsername, this.esPassword, this.readerSplitConfiguration);
        }

        @Override
        public void post() {
            super.post();
        }

        @Override
        public void postHandler(Configuration jobConfiguration) {
            super.postHandler(jobConfiguration);
        }

        @Override
        public void destroy() {
            try {
                this.esClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void startRead(RecordSender recordSender) {

            Scroll scroll = new Scroll(TimeValue.timeValueMinutes(this.keepAlive));

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.size(this.batchSize);

            if (StringUtils.isNotEmpty(this.query)) {
                searchSourceBuilder.query(QueryBuilders.wrapperQuery(this.query));
            }

            if (this.readerSplitConfiguration.getInt(Key.channelNum) > 1) {
                searchSourceBuilder.slice(new SliceBuilder(this.readerSplitConfiguration.getInt(Key.channelIndex), this.readerSplitConfiguration.getInt(Key.channelNum)));
            }


            SearchRequest searchRequest = new SearchRequest(this.esIndex);
            if (StringUtils.isNotBlank(this.esType)) {
                searchRequest.types(this.esType);
            }
            searchRequest.scroll(scroll);
            searchRequest.source(searchSourceBuilder);


            EsResultSet resultSet = new EsUtil().iterator(this.esClient, searchRequest, scroll);


            while (resultSet.hasNext()) {
                Record record = recordSender.createRecord();
                JSONObject line = resultSet.next();
                Iterator columnItera = this.esColumnMeta.iterator();
                while (columnItera.hasNext()) {
                    JSONObject column = (JSONObject) columnItera.next();

                    Object tempCol = line.get(column.getString(Key.COLUMN_NAME));
                    if (tempCol == null) {
                        record.addColumn(new StringColumn(""));
                    } else if (tempCol instanceof JSONObject) {
                        JSONObject jsonObject = (JSONObject) tempCol;
                        record.addColumn(new StringColumn(jsonObject.toJSONString()));
                    } else if (tempCol instanceof JSONArray) {
                        JSONArray jsonArray = (JSONArray) tempCol;
                        record.addColumn(new StringColumn(jsonArray.toJSONString()));
                    } else if (tempCol instanceof Double) {
                        record.addColumn(new DoubleColumn((Double) tempCol));
                    } else if (tempCol instanceof Boolean) {
                        record.addColumn(new BoolColumn((Boolean) tempCol));
                    } else if (tempCol instanceof Date) {
                        record.addColumn(new DateColumn((Date) tempCol));
                    } else if (tempCol instanceof Integer) {
                        record.addColumn(new LongColumn((Integer) tempCol));
                    } else if (tempCol instanceof Long) {
                        record.addColumn(new LongColumn((Long) tempCol));
                    } else {
                        record.addColumn(new StringColumn((String) tempCol));
                    }

                }
                recordSender.sendToWriter(record);
            }

            resultSet.close();


        }

    }

}