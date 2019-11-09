/*
 * Copyright(c) 2015-2019, Answer.AI.L
 * ShenZhen AAL Technology Co., Ltd.
 * All rights reserved.
 *
 * https://github.com/AnswerAIL/
 */
package org.answer.common.util.excel.acm;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.answer.common.util.excel.acm.entity.DataTypeEnum;
import org.answer.common.util.excel.acm.entity.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *     excel 解析测试用例
 * </p>
 *
 * @author Jaemon
 * @version 1.0
 * @date 2019-11-09
 */
@Slf4j
public class AesExcelApp {
    // 构成唯一值的 列名集合
    private static final List<String> UNIQUE_KEYS = Lists.newArrayList("押品编号", "押品分类");
    private static final String SEPERATOR = "#";

    // 索引哈希集<唯一值, 索引>
    private static final Map<String, Integer> INDEX_MAP = Maps.newHashMap();

    // 32,768
    private static final int BASE_INDEX = 15;
    private static final int BASE_NUMBER = 1 << BASE_INDEX;

    public static void main(String[] args) {
        String fileName = "src/test/resources/demo.xls";
        AesExcelListener aesExcelListener = new AesExcelListener();
        ExcelReader excelReader = EasyExcel.read(fileName, aesExcelListener).headRowNumber(1).build();

        List<ReadSheet> readSheets = excelReader.excelExecutor().sheetList();
        // 资产包主 sheet
        List<Map<String, String>> mainSheet = new ArrayList<>();
        // 押品个性化属性 sheet
        Map<Integer, List<Map<String, String>>> subSheets = new HashMap<>(readSheets.size() - 1);

        // sheet 解析数据集
        List<Map<String, String>> sheet;

        System.out.println("\n\n\n");
        for (ReadSheet readSheet : readSheets) {
            excelReader.read(readSheet);

            Integer sheetNo = readSheet.getSheetNo();
            sheet = new ArrayList<>();
            Map<String, String> row;

            String[] headers = aesExcelListener.header();
            List<Map<Integer, String>> datas = aesExcelListener.datas();
            // 每一个 sheet 的所有记录数据
            for (int i = 0; i < datas.size(); i++) {
                int index = sheetNo * BASE_NUMBER + i;
                Map<Integer, String> data = datas.get(i);
                row = new HashMap<>();
                // 记录由<Integer, String> 转成 <String[headName], String>
                for (Map.Entry<Integer, String> entry: data.entrySet()) {
                    row.put(headers[entry.getKey()], entry.getValue());
                }
                sheet.add(row);

                INDEX_MAP.put(uniqueKey(row), index);
            }

            // sheetNo=0, 说明是 主sheet
            if (sheetNo == 0) {
                mainSheet = sheet;
            } else {
                subSheets.put(sheetNo, sheet);
            }

            aesExcelListener.clear();
        }

//        log.info("mainSheet={}", JSON.toJSONString(mainSheet));
//        log.info("subSheets={}", JSON.toJSONString(subSheets));



        // 对 主sheet 中的记录字段进行扩展, 即: 将 副sheet 中的字段添加到 主sheet 中(依据： 多字段值构成的唯一值来判断)
        for (Map<String, String> eachRow : mainSheet) {
            String uniqueKey = uniqueKey(eachRow);
            if (INDEX_MAP.containsKey(uniqueKey)) {
                int idx = INDEX_MAP.get(uniqueKey);
                int sn = idx >> BASE_INDEX, index = idx % BASE_NUMBER;
                Map<String, String> subRow = subSheets.get(sn).get(index);
                eachRow.putAll(subRow);
            } else {
                log.info("主sheet中的uniqueKey={} 无法匹配到副sheet的记录", uniqueKey);
            }
        }

        System.out.println("\n\n\n");
        log.info("mainSheet={}", JSON.toJSONString(mainSheet));
        System.out.println("\n\n\n");



        Result result = new Result();
        List<Map<String, Object>> rltRows = Lists.newArrayList();
        Map<String, Object> rltRow;

        // 进行结果转换
        Map<String, Configuration> configs = Configuration.configs();
        for (Map<String, String> row : mainSheet) {
            rltRow = new HashMap<>(row.size());

            for (String key : row.keySet()) {
                String value = row.get(key);
                if (configs.containsKey(key)) {
                    Configuration config = configs.get(key);

                    // 转字典
                    String dictName = config.getDictName().trim();
                    if (!"".equalsIgnoreCase(dictName)) {
                        Map<String, String> dictMap = Configuration.DICTS.get(dictName);
                        // 如果字典不存在情况
                        if (dictMap.containsKey(value)) {
                            value = dictMap.get(value);
                        } else {
                            log.error("value={}不在字典dictName={}中, dictMap={}", value, dictName, dictMap);
                        }
                    }

                    // 转换类型
                    DataTypeEnum dataType = DataTypeEnum.matcher(config.getDataType());
                    Object newValue = dataType.parse(value, config.getDefaultKey());
                    rltRow.put(config.getKey(), newValue);
                } else {
                    log.error("key={}不在配置表中", key);
                }
            }
            rltRows.add(rltRow);
        }
        result.setDatas(rltRows);

        log.info("result={}", JSON.toJSONString(result));
        System.out.println(JSON.toJSONString(result));
    }


    private static String uniqueKey(final Map<String, String> row) {
        return UNIQUE_KEYS.stream().reduce((x, y) -> row.get(x) + SEPERATOR + row.get(y)).orElse("");
    }

}