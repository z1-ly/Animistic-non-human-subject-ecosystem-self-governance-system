package com.ecovoice.domain;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 生态价值大类 —— 人类可使用生态币向非人自然主体兑换的资源目录。
 */
@Getter
public enum EcologicalValueType {

    CARBON_SINK(
            1, "核心碳汇类", "森林 / 草原 / 海洋碳汇（固碳服务）",
            "全球森林、草原、海洋、湿地、冻土生态单元",
            "人类开展植树造林、退耕还林、修复湿地、治沙固碳、海洋蓝碳修复等行为，AI 实时核算新增固碳量，按吨折算发放对应生态币",
            "排放温室气体须消耗生态币向受损自然主体购买碳汇抵消额度；超标排放加倍扣费，资金全额流入对应自然主体国库",
            "吨 CO₂ 等价", 8.0, 1.0,
            Set.of("森林生态", "草原生态", "海洋生态", "河口湿地", "滨海湿地", "淡水湿地", "寒带生态", "荒漠生态", "林缘微境", "行星权利主体")
    ),

    WATER_SOURCE(
            2, "水环境调节类", "水源涵养 / 水权指标",
            "全球河流、湖泊、湿地、森林水源地生态单元",
            "人类修复水源地、河道清淤、水土保持等行为，AI 核算新增水源涵养量，发放对应生态币",
            "工业取水、城市供水、农业灌溉占用水源，须消耗生态币向流域主体购买用水额度，资金全额流入自然主体国库",
            "立方米", 0.5, 10.0,
            Set.of("森林生态", "草原生态", "河口湿地", "滨海湿地", "淡水湿地", "寒带生态", "林缘微境")
    ),

    WATER_PURIFICATION(
            3, "水环境调节类", "水质净化 / 纳污容量",
            "全球江河、湿地、红树林、海洋生态单元",
            "人类开展污水治理、人工湿地修复等行为，AI 核算新增纳污容量，发放对应生态币",
            "工厂、城镇排放废水占用水体纳污承载力，须消耗生态币赔付河湖主体；超标排污加倍扣费",
            "吨污染物当量", 6.0, 1.0,
            Set.of("海洋生态", "河口湿地", "滨海湿地", "淡水湿地", "森林生态")
    ),

    FLOOD_BUFFER(
            4, "水环境调节类", "洪水调蓄 / 旱涝缓冲服务",
            "全球沼泽、滩涂、林地、湖泊生态单元",
            "人类开展滩涂修复、退田还湖等行为，AI 核算新增调蓄容量，发放对应生态币",
            "城市硬化路面、填湖建房削弱防洪能力，须持续购买调蓄服务额度，向湿地、湖泊主体支付生态补偿",
            "立方米调蓄容量", 12.0, 1.0,
            Set.of("河口湿地", "滨海湿地", "淡水湿地", "森林生态", "草原生态", "城市生态")
    ),

    OXYGEN_RELEASE(
            5, "大气与气候调节类", "释氧权益",
            "全球森林、草原、海洋藻类生态单元",
            "人类开展植树造林、草原修复、城市绿化等行为，AI 核算新增释氧总量，发放对应生态币",
            "高密度城市、高耗能工业消耗大量氧气，须消耗生态币兑换自然释氧额度",
            "吨 O₂ 等价", 4.0, 1.0,
            Set.of("森林生态", "草原生态", "海洋生态", "城市生态", "林缘微境", "行星权利主体")
    ),

    AIR_PURIFICATION(
            6, "大气与气候调节类", "大气污染物净化权",
            "全球阔叶林、防护林带、山地植被生态单元",
            "人类种植防风固沙林、城市绿化等行为，AI 核算新增净化容量，发放对应生态币",
            "钢厂、火电、交通路网排放废气粉尘，须消耗生态币向林地主体购买空气净化服务",
            "吨污染物当量", 5.0, 1.0,
            Set.of("森林生态", "城市生态", "林缘微境", "草原生态", "荒漠生态")
    ),

    MICROCLIMATE_REGULATION(
            7, "大气与气候调节类", "局地温湿度调节服务（降温 / 防风）",
            "全球城市绿地、森林、滨海林带生态单元",
            "人类开展屋顶绿化、防风林种植等行为，AI 核算新增调节容量，发放对应生态币",
            "水泥热岛、露天高温厂区须购买自然降温 / 防风调节服务，向绿地、林带主体支付生态币",
            "调节服务单位", 15.0, 1.0,
            Set.of("城市生态", "森林生态", "滨海湿地", "林缘微境")
    ),

    SOIL_CONSERVATION(
            8, "土壤与地质稳定类", "水土保持 / 固土防侵蚀服务",
            "全球山地森林、草原、梯田生态单元",
            "人类开展边坡植被修复、水土流失治理等行为，AI 核算新增保持容量，发放对应生态币",
            "开山挖矿、过度开垦导致水土流失，自动扣除生态币赔付山地自然主体；严重破坏加倍扣费",
            "吨泥沙拦截量", 10.0, 1.0,
            Set.of("森林生态", "草原生态", "林缘微境", "寒带生态")
    ),

    SAND_FIXATION(
            9, "土壤与地质稳定类", "防风固沙 / 荒漠化抑制服务",
            "全球荒漠植被、防护林、绿洲生态单元",
            "人类开展治沙造林、沙生植被种植等行为，AI 核算新增固沙容量，发放对应生态币",
            "北方城镇、农田遭受风沙侵蚀，须持续向荒漠生态主体购买防风固沙服务额度",
            "公顷固沙面积", 20.0, 1.0,
            Set.of("荒漠生态", "草原生态", "森林生态")
    ),

    BIODIVERSITY_HABITAT(
            10, "生物多样性与生态支撑类", "生物栖息地保育额度（生物多样性信用）",
            "全球雨林、湿地、海洋、草原生态单元",
            "人类开展栖息地修复、生态廊道建设等行为，AI 核算新增保育价值，发放对应生态币",
            "占用原生栖息地须购买生物多样性补偿额度，生态币全额回流受损自然单元",
            "生物多样性信用单位", 25.0, 1.0,
            Set.of("森林生态", "海洋生态", "河口湿地", "滨海湿地", "淡水湿地", "草原生态", "林缘微境")
    ),

    POLLINATION(
            11, "生物多样性与生态支撑类", "自然授粉服务",
            "全球林地、野花草原、农田边界生态单元",
            "人类种植蜜源植物、保护传粉昆虫等行为，AI 核算新增授粉服务容量，发放对应生态币",
            "大面积果园、农田依赖野生昆虫授粉，须向周边林地、草原主体支付生态币采购授粉服务",
            "授粉服务 hectare·年", 8.0, 1.0,
            Set.of("森林生态", "草原生态", "林缘微境")
    ),

    MATERIAL_QUOTA(
            12, "生态物质与文化服务类", "生态物质供给开采配额",
            "全球森林、海洋、农田、山地生态单元",
            "人类开展可持续采伐、生态养殖等合规开发，AI 核算合规供给量，发放对应生态币",
            "开采林木、捕捞水产、开发矿产须消耗生态币购买自然物质开采配额；超额采集加倍扣费",
            "配额单位", 18.0, 1.0,
            Set.of("森林生态", "海洋生态", "草原生态", "林缘微境", "荒漠生态")
    ),

    CULTURE_RESEARCH(
            13, "生态物质与文化服务类", "生态文化游憩 / 科研使用权",
            "全球各类景观、原生生态单元",
            "人类开展生态研学基地建设、科研监测体系搭建，AI 核算服务容量，发放对应生态币",
            "文旅企业竞拍原生生态观光、研学时段；科研机构购买监测数据集须消耗对应生态币",
            "人次/观测点位·年", 3.0, 5.0,
            Set.of("森林生态", "海洋生态", "草原生态", "河口湿地", "滨海湿地", "淡水湿地", "寒带生态", "荒漠生态", "城市生态", "林缘微境", "行星权利主体")
    ),

    DAO_GOVERNANCE(
            14, "生态物质与文化服务类", "生态治理 DAO 投票权",
            "全球所有自然生态单元",
            "人类长期开展生态修复、持续贡献生态价值，可锁定生态币获得投票资格",
            "个人 / 企业锁定生态币，获得对应自然单元的治理提案投票权；锁仓代币沉淀在自然主体国库",
            "投票权·锁仓", 50.0, 1.0,
            Set.of("森林生态", "草原生态", "海洋生态", "河口湿地", "滨海湿地", "淡水湿地", "寒带生态", "荒漠生态", "城市生态", "林缘微境", "行星权利主体")
    );

    private final int rowId;
    private final String majorCategory;
    private final String coreValueIndicator;
    private final String naturalEntityScope;
    private final String issuanceLogic;
    private final String refluxLogic;
    private final String unit;
    /** 每单位生态币基准价格（ECO） */
    private final double ecoPricePerUnit;
    /** 默认兑换数量 */
    private final double defaultQuantity;
    private final Set<String> matchingEntityCategories;

    EcologicalValueType(
            int rowId, String majorCategory, String coreValueIndicator,
            String naturalEntityScope, String issuanceLogic, String refluxLogic,
            String unit, double ecoPricePerUnit, double defaultQuantity,
            Set<String> matchingEntityCategories) {
        this.rowId = rowId;
        this.majorCategory = majorCategory;
        this.coreValueIndicator = coreValueIndicator;
        this.naturalEntityScope = naturalEntityScope;
        this.issuanceLogic = issuanceLogic;
        this.refluxLogic = refluxLogic;
        this.unit = unit;
        this.ecoPricePerUnit = ecoPricePerUnit;
        this.defaultQuantity = defaultQuantity;
        this.matchingEntityCategories = matchingEntityCategories;
    }

    public String getValueTypeKey() {
        return name();
    }

    public double defaultTotalEco() {
        return ecoPricePerUnit * defaultQuantity;
    }

    public double calcTotalEco(double quantity) {
        return Math.round(ecoPricePerUnit * quantity * 100.0) / 100.0;
    }

    public static Optional<EcologicalValueType> fromKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(key.trim()))
                .findFirst();
    }

    public static List<EcologicalValueType> byMajorCategory(String majorCategory) {
        return Arrays.stream(values())
                .filter(v -> v.majorCategory.equals(majorCategory))
                .toList();
    }

    public boolean matchesEntityCategory(String category) {
        return category != null && matchingEntityCategories.contains(category);
    }
}
