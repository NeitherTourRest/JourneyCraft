package org.dsgroup.journeycraft.navigation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.dsgroup.journeycraft.navigation.entity.RoadNode;
import org.dsgroup.journeycraft.navigation.service.RoadNodeService;
import org.dsgroup.journeycraft.common.result.Response;
import org.dsgroup.journeycraft.scenic.entity.ScenicArea;
import org.dsgroup.journeycraft.scenic.mapper.ScenicAreaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 路网节点控制器
 * <p>
 * 简化版：仅提供基础CRUD接口
 * 
 * @author 后端智能体
 * @since 2026-04-14
 */
@Slf4j
@RestController
@RequestMapping("/api/navigation/nodes")
@Tag(name = "导航模块-路网节点", description = "路网节点管理接口")
public class RoadNodeController {

    @Autowired
    private RoadNodeService roadNodeService;

    @Autowired
    private ScenicAreaMapper scenicAreaMapper;

    @GetMapping("/list")
    @Operation(summary = "获取节点列表（分页）")
    public Response<IPage<RoadNode>> listNodes(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Page<RoadNode> page = new Page<>(pageNum, pageSize);
            return Response.ok(roadNodeService.page(page));
        } catch (Exception e) {
            log.error("查询节点列表失败", e);
            return Response.error("查询节点列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取节点详情")
    public Response<RoadNode> getNodeDetail(@PathVariable Long id) {
        try {
            RoadNode node = roadNodeService.getById(id);
            if (node == null) {
                return Response.error("节点不存在");
            }
            return Response.ok(node);
        } catch (Exception e) {
            log.error("查询节点详情失败，ID: {}", id, e);
            return Response.error("查询节点详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/osm/{osmId}")
    @Operation(summary = "根据OSM ID查询节点")
    public Response<RoadNode> getNodeByOsmId(@PathVariable Long osmId) {
        try {
            RoadNode node = roadNodeService.lambdaQuery()
                    .eq(RoadNode::getOsmId, osmId)
                    .one();
            if (node == null) {
                return Response.error("节点不存在");
            }
            return Response.ok(node);
        } catch (Exception e) {
            log.error("根据OSM ID查询节点失败，osmId: {}", osmId, e);
            return Response.error("根据OSM ID查询节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/scenic/{scenicAreaId}/primary")
    @Operation(summary = "获取景区代表节点", description = "返回该景区推荐在地图上标记的节点（POI优先）")
    public Response<RoadNode> getPrimaryNodeByScenicArea(
            @Parameter(description = "景区ID", required = true) @PathVariable Long scenicAreaId) {
        try {
            RoadNode node = roadNodeService.lambdaQuery()
                    .eq(RoadNode::getScenicAreaId, scenicAreaId)
                    .eq(RoadNode::getIsPrimary, true)
                    .one();
            if (node == null) {
                return Response.error("该景区没有代表节点");
            }
            // Populate scenic area name for frontend display
            ScenicArea scenic = scenicAreaMapper.selectById(scenicAreaId);
            if (scenic != null) {
                node.setScenicAreaName(scenic.getName());
            }
            return Response.ok(node);
        } catch (Exception e) {
            log.error("查询景区代表节点失败，景区ID: {}", scenicAreaId, e);
            return Response.error("查询景区代表节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/scenic/{scenicAreaId}/entrances")
    @Operation(summary = "获取景区出入口节点集合", description = "返回景区的主要出入口节点（入口type=0和POI type=2），最多5个")
    public Response<List<RoadNode>> getEntranceNodes(
            @Parameter(description = "景区ID", required = true) @PathVariable Long scenicAreaId) {
        try {
            List<RoadNode> nodes = roadNodeService.lambdaQuery()
                    .eq(RoadNode::getScenicAreaId, scenicAreaId)
                    .eq(RoadNode::getEnabled, true)
                    .in(RoadNode::getNodeType, 0, 2)  // 入口(0) + POI(2)
                    .orderByAsc(RoadNode::getIsPrimary)  // is_primary 优先
                    .last("LIMIT 5")
                    .list();
            // Populate scenic area name
            ScenicArea scenic = scenicAreaMapper.selectById(scenicAreaId);
            String scenicName = scenic != null ? scenic.getName() : null;
            if (scenicName != null) {
                for (RoadNode node : nodes) {
                    node.setScenicAreaName(scenicName);
                }
            }
            return Response.ok(nodes);
        } catch (Exception e) {
            log.error("查询景区出入口失败，景区ID: {}", scenicAreaId, e);
            return Response.error("查询景区出入口失败: " + e.getMessage());
        }
    }

    @GetMapping("/scenic/{scenicAreaId}")
    @Operation(summary = "查询景区的节点")
    public Response<List<RoadNode>> getNodesByScenicArea(@PathVariable Long scenicAreaId) {
        try {
            // Fetch the scenic area name for frontend display
            ScenicArea scenic = scenicAreaMapper.selectById(scenicAreaId);
            String scenicName = scenic != null ? scenic.getName() : null;

            List<RoadNode> nodes = roadNodeService.lambdaQuery()
                    .eq(RoadNode::getScenicAreaId, scenicAreaId)
                    .list();
            // Populate scenic area name on each node
            if (scenicName != null) {
                for (RoadNode node : nodes) {
                    node.setScenicAreaName(scenicName);
                }
            }
            return Response.ok(nodes);
        } catch (Exception e) {
            log.error("查询景区节点失败，景区ID: {}", scenicAreaId, e);
            return Response.error("查询景区节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    @Operation(summary = "搜索景区内节点（按名称或OSM标签）")
    public Response<IPage<RoadNode>> searchNodes(
            @RequestParam Long scenicAreaId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Page<RoadNode> page = new Page<>(pageNum, pageSize);
            // 对 keyword 中的 SQL LIKE 通配符进行转义，防止 SQL 注入
            String escapedKeyword = keyword.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            roadNodeService.lambdaQuery()
                    .eq(RoadNode::getScenicAreaId, scenicAreaId)
                    .and(w -> w
                            .like(RoadNode::getName, escapedKeyword)
                            .or()
                            .like(RoadNode::getOsmTags, escapedKeyword)
                    )
                    .orderByAsc(RoadNode::getName)
                    .page(page);
            // Populate scenic area name for frontend display
            ScenicArea scenic = scenicAreaMapper.selectById(scenicAreaId);
            if (scenic != null && page.getRecords() != null) {
                for (RoadNode node : page.getRecords()) {
                    node.setScenicAreaName(scenic.getName());
                }
            }
            return Response.ok(page);
        } catch (Exception e) {
            log.error("搜索节点失败，景区ID: {}, 关键词: {}", scenicAreaId, keyword, e);
            return Response.error("搜索节点失败: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "创建节点")
    public Response<RoadNode> createNode(@RequestBody RoadNode node) {
        try {
            roadNodeService.save(node);
            return Response.ok(node);
        } catch (Exception e) {
            log.error("创建节点失败", e);
            return Response.error("创建节点失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新节点")
    public Response<RoadNode> updateNode(@PathVariable Long id, @RequestBody RoadNode node) {
        try {
            node.setId(id);
            roadNodeService.updateById(node);
            return Response.ok(node);
        } catch (Exception e) {
            log.error("更新节点失败，ID: {}", id, e);
            return Response.error("更新节点失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除节点")
    public Response<Void> deleteNode(@PathVariable Long id) {
        try {
            roadNodeService.removeById(id);
            return Response.ok();
        } catch (Exception e) {
            log.error("删除节点失败，ID: {}", id, e);
            return Response.error("删除节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Response<String> healthCheck() {
        try {
            return Response.ok("节点服务正常，当前总数: " + roadNodeService.count());
        } catch (Exception e) {
            log.error("节点服务异常", e);
            return Response.error("节点服务异常: " + e.getMessage());
        }
    }
}
