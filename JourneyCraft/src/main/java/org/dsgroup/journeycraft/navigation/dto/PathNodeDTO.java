package org.dsgroup.journeycraft.navigation.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 路径节点 DTO。
 * <p>
 * 从 PathPlanningService.PathNode 提取的独立 DTO，
 * 供 NavigationService API 对外暴露，避免依赖内部 service 类型。
 */
@Data
public class PathNodeDTO {

    /** 节点ID */
    private Long nodeId;

    /** 节点名称 */
    private String name;

    /** 顺序号（0 起点） */
    private Integer sequence;

    /** WGS-84 纬度 */
    private BigDecimal latitude;

    /** WGS-84 经度 */
    private BigDecimal longitude;

    /** 动作: start / visit / end */
    private String action;

    /** 到达时间（预留，当前为 null） */
    private String arrivalTime;

    /** 是否景区代表节点（用于前端地图标记突出显示） */
    private Boolean isPrimary;

    /** 是否为目标景区的入口节点（景区间导航时使用） */
    private Boolean isEntryNode;

    /** 是否为起点景区的出口节点（景区间导航时使用） */
    private Boolean isExitNode;

    /** 所属景区ID（用于前端景区级导航） */
    private Long scenicAreaId;

    /** 所属景区名称（用于前端显示） */
    private String scenicAreaName;
}
