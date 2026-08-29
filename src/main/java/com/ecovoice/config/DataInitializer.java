package com.ecovoice.config;



import com.ecovoice.domain.*;

import com.ecovoice.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;

import org.springframework.core.annotation.Order;

import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;



import java.util.LinkedHashMap;

import java.util.Map;



@Component

@Order(1)

@RequiredArgsConstructor

public class DataInitializer implements CommandLineRunner {



    private final NaturalEntityRepository entityRepository;

    private final SensorNodeRepository nodeRepository;

    private final NetworkConfigRepository networkRepository;

    private final SmartContractRepository smartContractRepository;



    private static final Map<String, EcoSystemDef> ECOSYSTEMS = new LinkedHashMap<>();



    static {

        ECOSYSTEMS.put("西双版纳", new EcoSystemDef(

                "西双版纳热带雨林", "森林生态", "云南 · 西双版纳",

                "中国最大热带雨林生态系统，生物多样性热点区域，通过雨林传感网络监测气候变化与生态胁迫。",

                "#2e7d32", ProtocolType.MQTT, "mqtt://broker.ecovoice.local:1883",

                new String[][]{

                        {"ND-R01", "冠层空气湿度", "AIR_HUMIDITY", "林冠层"},

                        {"ND-R02", "林下土壤湿度", "SOIL_MOISTURE", "腐殖质层"},

                        {"ND-R03", "林内温度", "AIR_TEMPERATURE", "郁闭核心区"},

                        {"ND-R04", "溪流pH", "WATER_PH", "支流监测点"},

                        {"ND-R05", "猿鸣声纹", "SOUND_DECIBEL", "灵长类栖息地"}

                }));

        ECOSYSTEMS.put("青藏高原", new EcoSystemDef(

                "青藏高原冻原", "寒带生态", "西藏 · 羌塘高原",

                "世界屋脊冻原生态系统，对升温与融冻极为敏感，可悬赏保温与扰动控制。",

                "#81d4fa", ProtocolType.COAP, "coap://gateway.ecovoice.local/vitals",

                new String[][]{

                        {"ND-T01", "地表温度", "AIR_TEMPERATURE", "冻土监测站"},

                        {"ND-T02", "苔原土壤湿度", "SOIL_MOISTURE", "浅层冻土层"},

                        {"ND-T03", "融冻水域pH", "WATER_PH", "热融湖"},

                        {"ND-T04", "高原空气湿度", "AIR_HUMIDITY", "低空层"},

                        {"ND-T05", "藏羚羊迁徙声场", "SOUND_DECIBEL", "迁徙廊道"}

                }));

        ECOSYSTEMS.put("内蒙古草原", new EcoSystemDef(

                "内蒙古草原生态系统", "草原生态", "内蒙古 · 锡林郭勒",

                "中国典型温带草原，对干旱与过牧高度敏感，通过草根网络监测土壤墒情并发布补水与休牧诉求。",

                "#aed581", ProtocolType.MQTT, "mqtt://broker.ecovoice.local:1883",

                new String[][]{

                        {"ND-S01", "草原土壤湿度", "SOIL_MOISTURE", "牧草地表"},

                        {"ND-S02", "草原气温", "AIR_TEMPERATURE", "开阔地"},

                        {"ND-S03", "草原空气湿度", "AIR_HUMIDITY", "近地面层"},

                        {"ND-S04", "季节性湖泊pH", "WATER_PH", "洼地水体"},

                        {"ND-S05", "牧区声景", "SOUND_DECIBEL", "放牧带"}

                }));

        ECOSYSTEMS.put("塔克拉玛干", new EcoSystemDef(

                "塔克拉玛干沙漠绿洲", "荒漠生态", "新疆 · 南疆绿洲",

                "中国最大沙漠边缘绿洲，水资源极度稀缺，以精确补水悬赏与沙尘胁迫预警为核心诉求。",

                "#ffb74d", ProtocolType.MODBUS_RTU, "COM5@9600",

                new String[][]{

                        {"ND-D01", "绿洲土壤湿度", "SOIL_MOISTURE", "胡杨根区"},

                        {"ND-D02", "沙地表面温度", "AIR_TEMPERATURE", "沙丘顶"},

                        {"ND-D03", "绿洲水体pH", "WATER_PH", "坎儿井出口"},

                        {"ND-D04", "荒漠空气湿度", "AIR_HUMIDITY", "绿洲边缘"},

                        {"ND-D05", "风沙声纹", "SOUND_DECIBEL", "风蚀监测点"}

                }));

        ECOSYSTEMS.put("南海珊瑚礁", new EcoSystemDef(

                "南海珊瑚礁海域", "海洋生态", "海南 · 三沙海域",

                "中国南海珊瑚礁生态系统，对海水酸化与热胁迫极度敏感，可悬赏降温、减污与游客扰动管控。",

                "#00bcd4", ProtocolType.HTTP_REST, "/api/ingest/http",

                new String[][]{

                        {"ND-C01", "珊瑚礁水体pH", "WATER_PH", "礁盘浅水区"},

                        {"ND-C02", "表层海水温度", "AIR_TEMPERATURE", "海面浮标"},

                        {"ND-C03", "礁区湿度(盐雾)", "AIR_HUMIDITY", "岛礁气象站"},

                        {"ND-C04", "礁盘沉积湿度", "SOIL_MOISTURE", "潮间带"},

                        {"ND-C05", "鲸歌与礁区声景", "SOUND_DECIBEL", "海洋保护区"}

                }));

        ECOSYSTEMS.put("长江口", new EcoSystemDef(

                "长江口湿地", "河口湿地", "上海 · 长江三角洲",

                "中国最大河口湿地，水质与潮汐节律高度耦合，独立发布污染治理与水文调控诉求。",

                "#4dd0e1", ProtocolType.MQTT, "mqtt://broker.ecovoice.local:1883",

                new String[][]{

                        {"ND-M01", "河口pH", "WATER_PH", "咸淡水交汇区"},

                        {"ND-M02", "滩涂土壤湿度", "SOIL_MOISTURE", "芦苇滩"},

                        {"ND-M03", "河口气温", "AIR_TEMPERATURE", "开阔水面"},

                        {"ND-M04", "河口空气湿度", "AIR_HUMIDITY", "沿岸带"},

                        {"ND-M05", "候鸟声纹", "SOUND_DECIBEL", "迁徙驿站"}

                }));

        ECOSYSTEMS.put("红树林", new EcoSystemDef(

                "东寨港红树林", "滨海湿地", "海南 · 海口",

                "中国最大红树林自然保护区，对海水入侵与岸线开发敏感，拥有独立法人身份与数据收益权。",

                "#00897b", ProtocolType.WEBSOCKET, "/ws/vitals",

                new String[][]{

                        {"ND-K01", "根系区盐度代理pH", "WATER_PH", "潮间带"},

                        {"ND-K02", "红树林土壤湿度", "SOIL_MOISTURE", "气生根区"},

                        {"ND-K03", "海岸气温", "AIR_TEMPERATURE", "林冠层"},

                        {"ND-K04", "海岸湿度", "AIR_HUMIDITY", "滨海气象站"},

                        {"ND-K05", "蟹类与鸟类声景", "SOUND_DECIBEL", "滩涂核心区"}

                }));

        ECOSYSTEMS.put("大兴安岭", new EcoSystemDef(

                "大兴安岭针叶林", "森林生态", "黑龙江 · 大兴安岭",

                "中国最大原始针叶林碳汇主体，对干旱、火灾风险与病虫害敏感，可发布防火与补水悬赏。",

                "#558b2f", ProtocolType.MQTT, "mqtt://broker.ecovoice.local:1883",

                new String[][]{

                        {"ND-F01", "林内土壤湿度", "SOIL_MOISTURE", "苔藓层"},

                        {"ND-F02", "林冠气温", "AIR_TEMPERATURE", "郁闭层"},

                        {"ND-F03", "林内空气湿度", "AIR_HUMIDITY", "近地面"},

                        {"ND-F04", "林间溪流pH", "WATER_PH", "腐殖质溪流"},

                        {"ND-F05", "松鸡声纹", "SOUND_DECIBEL", "原始林核心区"}

                }));

        ECOSYSTEMS.put("镜湖", new EcoSystemDef(

                "杭州镜湖小微湿地", "淡水湿地", "浙江 · 杭州",

                "以水生植物与底栖生物为核心的城市湿地自然体，监测水质与声环境并发布精确治理诉求。",

                "#00e5ff", ProtocolType.MQTT, "mqtt://broker.ecovoice.local:1883",

                new String[][]{

                        {"ND-P01", "入水口pH探针", "WATER_PH", "入水口浮台"},

                        {"ND-P02", "中心水域pH", "WATER_PH", "湖心浮标"},

                        {"ND-P03", "岸带噪声监测", "SOUND_DECIBEL", "东岸栈道"},

                        {"ND-P04", "空气湿度节点", "AIR_HUMIDITY", "观景亭"},

                        {"ND-P05", "空气温度节点", "AIR_TEMPERATURE", "观景亭"}

                }));

        ECOSYSTEMS.put("绿岛", new EcoSystemDef(

                "北京城市立体绿岛", "城市生态", "北京 · 中关村",

                "城市中的小微自然体，代表建成区非人类权利主体，持续监测热岛效应与土壤墒情。",

                "#76ff03", ProtocolType.HTTP_REST, "/api/ingest/http",

                new String[][]{

                        {"ND-G01", "种植床土壤湿度A", "SOIL_MOISTURE", "Bed-A"},

                        {"ND-G02", "种植床土壤湿度B", "SOIL_MOISTURE", "Bed-B"},

                        {"ND-G03", "冠层空气温度", "AIR_TEMPERATURE", "棚架顶"},

                        {"ND-G04", "冠层空气湿度", "AIR_HUMIDITY", "棚架顶"},

                        {"ND-G05", "蜜蜂访花声场", "SOUND_DECIBEL", "蜂箱旁"}

                }));

        ECOSYSTEMS.put("竹海", new EcoSystemDef(

                "安吉竹海林缘微境", "林缘微境", "浙江 · 安吉",

                "中国最大竹乡的竹林微气候岛，代表局部非人类实体的独立发声与链上悬赏能力。",

                "#b388ff", ProtocolType.MODBUS_RTU, "COM3@9600",

                new String[][]{

                        {"ND-B01", "林下土壤湿度", "SOIL_MOISTURE", "竹根带"},

                        {"ND-B02", "林缘空气温度", "AIR_TEMPERATURE", "石径边"},

                        {"ND-B03", "林缘空气湿度", "AIR_HUMIDITY", "石径边"},

                        {"ND-B04", "风竹声纹采集", "SOUND_DECIBEL", "听竹轩"},

                        {"ND-B05", "雨水收集池pH", "WATER_PH", "蓄水池"}

                }));

    }



    @Override
    public void run(String... args) {
        // 先初始化生态系统
        if (entityRepository.count() == 0) {
            ECOSYSTEMS.values().forEach(this::createEcoSystem);
        } else {
            ECOSYSTEMS.forEach((key, def) -> ensureEcoSystem(key, def));
        }

        // 初始化智能合约资金池（放在最后，确保表已创建）
        try {
            if (smartContractRepository.count() == 0) {
                SmartContract smartContract = SmartContract.builder()
                        .contractName("主生态币资金池")
                        .totalSupply(10000.0)
                        .availableBalance(10000.0)
                        .lentBalance(0.0)
                        .description("生态币恒定总量资金池，用于生态修复借贷和分配。环境实体需要借币来发布悬赏任务，任务完成后归还生态币。")
                        .build();
                smartContractRepository.save(smartContract);
                System.out.println("智能合约资金池初始化完成 - 总量: 1,000,000 生态币");
            }
        } catch (Exception e) {
            System.err.println("智能合约资金池初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private void ensureEcoSystem(String key, EcoSystemDef def) {

        boolean exists = entityRepository.findAll().stream()

                .anyMatch(e -> e.getName().contains(key) || e.getName().equals(def.name));

        if (!exists) {

            createEcoSystem(def);

        }

    }



    private void createEcoSystem(EcoSystemDef def) {

        NaturalEntity entity = entityRepository.save(NaturalEntity.builder()

                .name(def.name)

                .category(def.category)

                .location(def.location)

                .description(def.description)

                .avatarColor(def.color)

                .status("ACTIVE")

                .build());

        createNodes(entity.getId(), def.nodes);

        networkRepository.save(NetworkConfig.builder()

                .entityId(entity.getId())

                .protocol(def.protocol)

                .endpoint(def.endpoint)

                .topic("ecovoice/entity/" + entity.getId() + "/vitals")

                .nodeCount(def.nodes.length)

                .active(true)

                .build());

    }



    private void createNodes(Long entityId, String[][] rows) {

        for (String[] row : rows) {

            nodeRepository.save(SensorNode.builder()

                    .entityId(entityId)

                    .nodeCode(row[0])

                    .name(row[1])

                    .sensorType(SensorType.valueOf(row[2]))

                    .position(row[3])

                    .online(false)

                    .build());

        }

    }



    private record EcoSystemDef(

            String name,

            String category,

            String location,

            String description,

            String color,

            ProtocolType protocol,

            String endpoint,

            String[][] nodes) {

    }

}

