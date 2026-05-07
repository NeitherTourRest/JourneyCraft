package org.dsgroup.journeycraft.navigation.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dsgroup.journeycraft.navigation.entity.RoadEdge;
import org.dsgroup.journeycraft.navigation.entity.RoadNode;
import org.dsgroup.journeycraft.navigation.service.PathPlanningService;
import org.dsgroup.journeycraft.navigation.service.RoadEdgeService;
import org.dsgroup.journeycraft.navigation.service.RoadNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 路径规划算法服务实现
 * <p>
 * 基于 {@link InMemoryGraph} 实现 Dijkstra/A* 等算法，完全消除路径规划过程中的数据库查询。
 * 如果内存图未加载，自动降级为 DB 查询。
 * 
 * @author 后端智能体
 * @since 2026-04-14
 */
@Slf4j
@Service
public class PathPlanningServiceImpl implements PathPlanningService {

    @Autowired
    private RoadNodeService roadNodeService;

    @Autowired
    private RoadEdgeService roadEdgeService;

    @Autowired
    private InMemoryGraph graph;

    // ==================== 公共 API（签名保持不变） ====================

    @Override
    public PathPlanningResult calculateShortestPath(Long startNodeId, Long endNodeId,
                                                       Integer transportMode, String strategy) {
        log.info("计算最短路径: 起点={}, 终点={}, 交通方式={}, 策略={}", 
                 startNodeId, endNodeId, transportMode, strategy);

        RoadNode startNode = roadNodeService.getById(startNodeId);
        RoadNode endNode = roadNodeService.getById(endNodeId);
        if (startNode == null || endNode == null) {
            log.warn("起点或终点不存在");
            return null;
        }

        BigDecimal totalDistance;
        int totalTime;
        List<PathNode> pathNodes;

        if (graph.isLoaded()) {
            PathResult pr = dijkstra(startNodeId, endNodeId, transportMode, strategy);
            if (pr == null) {
                log.warn("未找到可行路径");
                return null;
            }
            pathNodes = pr.nodes;
            totalDistance = BigDecimal.valueOf(pr.totalDistance);
            totalTime = Math.round(pr.totalTime);
        } else {
            log.warn("InMemoryGraph 未加载，降级为 DB 查询");
            pathNodes = dijkstraDb(startNodeId, endNodeId, transportMode, strategy);
            if (pathNodes == null || pathNodes.isEmpty()) {
                log.warn("未找到可行路径");
                return null;
            }
            totalDistance = BigDecimal.ZERO;
            totalTime = 0;
            for (int i = 0; i < pathNodes.size() - 1; i++) {
                BigDecimal dist = getDistanceBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId());
                if (dist != null) totalDistance = totalDistance.add(dist);
                Integer tm = getTimeBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId(), transportMode);
                if (tm != null) totalTime += tm;
            }
        }

        PathPlanningResult result = new PathPlanningResult();
        result.setTotalDistance(totalDistance);
        result.setEstimatedTime(totalTime);
        result.setTransportMode(getTransportModeName(transportMode));
        result.setStrategy(strategy != null ? strategy : "shortest_distance");
        result.setNodes(pathNodes);
        return result;
    }

    @Override
    public PathPlanningResult calculateAStarPath(Long startNodeId, Long endNodeId,
                                                    Integer transportMode, String strategy) {
        log.info("A*路径规划: 起点={}, 终点={}, 交通方式={}, 策略={}", 
                 startNodeId, endNodeId, transportMode, strategy);

        RoadNode startNode = roadNodeService.getById(startNodeId);
        RoadNode endNode = roadNodeService.getById(endNodeId);
        if (startNode == null || endNode == null) {
            log.warn("起点或终点不存在");
            return null;
        }

        BigDecimal totalDistance;
        int totalTime;
        List<PathNode> pathNodes;

        if (graph.isLoaded()) {
            PathResult pr = aStar(startNodeId, endNodeId, transportMode, strategy);
            if (pr == null) {
                log.warn("A*未找到可行路径");
                return null;
            }
            pathNodes = pr.nodes;
            totalDistance = BigDecimal.valueOf(pr.totalDistance);
            totalTime = Math.round(pr.totalTime);
        } else {
            log.warn("InMemoryGraph 未加载，降级为 DB 查询");
            pathNodes = aStarDb(startNodeId, endNodeId, transportMode, strategy);
            if (pathNodes == null || pathNodes.isEmpty()) {
                log.warn("A*未找到可行路径");
                return null;
            }
            totalDistance = BigDecimal.ZERO;
            totalTime = 0;
            for (int i = 0; i < pathNodes.size() - 1; i++) {
                BigDecimal dist = getDistanceBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId());
                if (dist != null) totalDistance = totalDistance.add(dist);
                Integer tm = getTimeBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId(), transportMode);
                if (tm != null) totalTime += tm;
            }
        }

        PathPlanningResult result = new PathPlanningResult();
        result.setTotalDistance(totalDistance);
        result.setEstimatedTime(totalTime);
        result.setTransportMode(getTransportModeName(transportMode));
        result.setStrategy(strategy != null ? strategy : "shortest_distance");
        result.setNodes(pathNodes);
        return result;
    }

    @Override
    public MultiTargetRouteResult calculateMultiTargetRoute(Long startNodeId, List<Long> endNodeIds,
                                                               Integer transportMode, boolean needReturn) {
        log.info("计算多目标路线: 起点={}, 目标数={}, 返回={}", 
                 startNodeId, endNodeIds != null ? endNodeIds.size() : 0, needReturn);

        if (endNodeIds == null || endNodeIds.isEmpty()) return null;

        String strategy = "shortest_distance";
        MultiTargetRouteResult result = new MultiTargetRouteResult();
        List<PathPlanningResult> segments = new ArrayList<>();
        List<Long> visitOrder = new ArrayList<>();
        BigDecimal totalDistance = BigDecimal.ZERO;
        int totalTime = 0;

        Long currentNode = startNodeId;
        List<Long> remainingTargets = new ArrayList<>(endNodeIds);

        while (!remainingTargets.isEmpty()) {
            Long nearestNode = findNearestNode(currentNode, remainingTargets, transportMode, strategy);
            if (nearestNode == null) break;

            PathPlanningResult segment = calculateShortestPath(currentNode, nearestNode, transportMode, strategy);
            if (segment != null) {
                segments.add(segment);
                totalDistance = totalDistance.add(segment.getTotalDistance());
                totalTime += segment.getEstimatedTime();
                visitOrder.add(nearestNode);
            }

            currentNode = nearestNode;
            remainingTargets.remove(nearestNode);
        }

        if (needReturn && !visitOrder.isEmpty()) {
            PathPlanningResult returnSegment = calculateShortestPath(currentNode, startNodeId, transportMode, strategy);
            if (returnSegment != null) {
                segments.add(returnSegment);
                totalDistance = totalDistance.add(returnSegment.getTotalDistance());
                totalTime += returnSegment.getEstimatedTime();
            }
            result.setReturnedToStart(true);
        } else {
            result.setReturnedToStart(false);
        }

        result.setTotalDistance(totalDistance);
        result.setTotalTime(totalTime);
        result.setVisitOrder(visitOrder);
        result.setSegments(segments);
        return result;
    }

    @Override
    public PathPlanningResult calculateShortestPathToScenic(Long scenicAreaId, Long startNodeId,
                                                              Integer transportMode, String strategy) {
        log.info("景区多入口路径规划: 景区ID={}, 起点={}, 交通方式={}, 策略={}",
                 scenicAreaId, startNodeId, transportMode, strategy);

        // 查询景区关联的所有路网入口节点
        List<RoadNode> scenicNodes = roadNodeService.lambdaQuery()
                .eq(RoadNode::getScenicAreaId, scenicAreaId)
                .eq(RoadNode::getEnabled, true)
                .list();

        if (scenicNodes == null || scenicNodes.isEmpty()) {
            log.warn("景区 {} 没有关联的入口节点", scenicAreaId);
            return null;
        }

        Set<Long> targetSet = scenicNodes.stream()
                .map(RoadNode::getId)
                .collect(Collectors.toSet());

        log.info("景区 {} 有 {} 个入口节点", scenicAreaId, targetSet.size());

        RoadNode startNode = roadNodeService.getById(startNodeId);
        if (startNode == null) {
            log.warn("起点 {} 不存在", startNodeId);
            return null;
        }

        BigDecimal totalDistance;
        int totalTime;
        List<PathNode> pathNodes;

        if (graph.isLoaded()) {
            PathResult pr = dijkstraToTargets(startNodeId, targetSet, transportMode, strategy);
            if (pr == null) {
                log.warn("未找到从 {} 到景区 {} 的路径", startNodeId, scenicAreaId);
                return null;
            }
            pathNodes = pr.nodes;
            totalDistance = BigDecimal.valueOf(pr.totalDistance);
            totalTime = Math.round(pr.totalTime);
        } else {
            pathNodes = dijkstraToTargetsDb(startNodeId, targetSet, transportMode, strategy);
            if (pathNodes == null || pathNodes.isEmpty()) {
                log.warn("未找到从 {} 到景区 {} 的路径", startNodeId, scenicAreaId);
                return null;
            }
            totalDistance = BigDecimal.ZERO;
            totalTime = 0;
            for (int i = 0; i < pathNodes.size() - 1; i++) {
                BigDecimal dist = getDistanceBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId());
                if (dist != null) totalDistance = totalDistance.add(dist);
                Integer tm = getTimeBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId(), transportMode);
                if (tm != null) totalTime += tm;
            }
        }

        PathPlanningResult result = new PathPlanningResult();
        result.setTotalDistance(totalDistance);
        result.setEstimatedTime(totalTime);
        result.setTransportMode(getTransportModeName(transportMode));
        result.setStrategy(strategy != null ? strategy : "shortest_distance");
        result.setNodes(pathNodes);

        log.info("景区多入口规划完成: 起点={}, 命中入口={}, 距离={}m",
                startNodeId, pathNodes.get(pathNodes.size() - 1).getNodeId(), totalDistance);
        return result;
    }

    @Override
    public PathPlanningResult calculateShortestPathBetweenScenicAreas(
            Long startScenicAreaId, Long endScenicAreaId,
            Integer transportMode, String strategy) {

        log.info("景区间路径规划: 起点景区={}, 终点景区={}, 交通方式={}, 策略={}",
                 startScenicAreaId, endScenicAreaId, transportMode, strategy);

        List<RoadNode> startNodes = roadNodeService.lambdaQuery()
                .eq(RoadNode::getScenicAreaId, startScenicAreaId)
                .eq(RoadNode::getEnabled, true)
                .list();
        if (startNodes == null || startNodes.isEmpty()) {
            log.warn("起点景区 {} 没有关联的路网节点", startScenicAreaId);
            return null;
        }

        List<RoadNode> endNodes = roadNodeService.lambdaQuery()
                .eq(RoadNode::getScenicAreaId, endScenicAreaId)
                .eq(RoadNode::getEnabled, true)
                .list();
        if (endNodes == null || endNodes.isEmpty()) {
            log.warn("终点景区 {} 没有关联的路网节点", endScenicAreaId);
            return null;
        }

        Set<Long> startNodeIds = startNodes.stream().map(RoadNode::getId).collect(Collectors.toSet());
        Set<Long> targetSet = endNodes.stream().map(RoadNode::getId).collect(Collectors.toSet());

        log.info("多源Dijkstra: {} 个起点, {} 个终点", startNodeIds.size(), targetSet.size());

        BigDecimal totalDistance;
        int totalTime;
        List<PathNode> pathNodes;

        if (graph.isLoaded()) {
            PathResult pr = dijkstraMultiSourceToTargets(startNodeIds, targetSet, transportMode, strategy);
            if (pr == null) {
                log.warn("未找到从景区 {} 到景区 {} 的路径", startScenicAreaId, endScenicAreaId);
                return null;
            }
            pathNodes = pr.nodes;
            totalDistance = BigDecimal.valueOf(pr.totalDistance);
            totalTime = Math.round(pr.totalTime);
        } else {
            pathNodes = dijkstraMultiSourceToTargetsDb(startNodeIds, targetSet, transportMode, strategy);
            if (pathNodes == null || pathNodes.isEmpty()) {
                log.warn("未找到从景区 {} 到景区 {} 的路径", startScenicAreaId, endScenicAreaId);
                return null;
            }
            totalDistance = BigDecimal.ZERO;
            totalTime = 0;
            for (int i = 0; i < pathNodes.size() - 1; i++) {
                BigDecimal dist = getDistanceBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId());
                if (dist != null) totalDistance = totalDistance.add(dist);
                Integer tm = getTimeBetweenNodes(pathNodes.get(i).getNodeId(),
                        pathNodes.get(i + 1).getNodeId(), transportMode);
                if (tm != null) totalTime += tm;
            }
        }

        // Mark the first node as exit from start scenic area, the last node as entry to end scenic area
        if (pathNodes != null && !pathNodes.isEmpty()) {
            pathNodes.get(0).setIsExitNode(true);
            pathNodes.get(pathNodes.size() - 1).setIsEntryNode(true);
        }

        PathPlanningResult result = new PathPlanningResult();
        result.setTotalDistance(totalDistance);
        result.setEstimatedTime(totalTime);
        result.setTransportMode(getTransportModeName(transportMode));
        result.setStrategy(strategy != null ? strategy : "shortest_distance");
        result.setNodes(pathNodes);
        return result;
    }

    @Override
    public BigDecimal getDistanceBetweenNodes(Long fromNodeId, Long toNodeId) {
        if (graph.isLoaded()) {
            int fromIdx = graph.getNodeIndex(fromNodeId);
            int toIdx = graph.getNodeIndex(toNodeId);
            if (fromIdx < 0 || toIdx < 0) return null;
            float dist = graph.getEdgeWeight(fromIdx, toIdx);
            if (dist < 0) {
                // Try reverse direction
                dist = graph.getEdgeWeight(toIdx, fromIdx);
            }
            return dist >= 0 ? BigDecimal.valueOf(dist) : null;
        }
        // DB fallback
        RoadEdge edge = roadEdgeService.lambdaQuery()
                .eq(RoadEdge::getFromNodeId, fromNodeId)
                .eq(RoadEdge::getToNodeId, toNodeId)
                .one();
        if (edge == null) {
            edge = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, toNodeId)
                    .eq(RoadEdge::getToNodeId, fromNodeId)
                    .one();
        }
        return edge != null ? edge.getDistance() : null;
    }

    @Override
    public Integer getTimeBetweenNodes(Long fromNodeId, Long toNodeId, Integer transportMode) {
        if (graph.isLoaded()) {
            int fromIdx = graph.getNodeIndex(fromNodeId);
            int toIdx = graph.getNodeIndex(toNodeId);
            if (fromIdx < 0 || toIdx < 0) return null;
            int effectiveMode = transportMode != null ? transportMode : 1;
            int t = graph.getTimeForEdge(fromIdx, toIdx, effectiveMode);
            if (t < 0) {
                // Try reverse direction
                t = graph.getTimeForEdge(toIdx, fromIdx, effectiveMode);
            }
            return t >= 0 ? t : null;
        }
        // DB fallback
        RoadEdge edge = roadEdgeService.lambdaQuery()
                .eq(RoadEdge::getFromNodeId, fromNodeId)
                .eq(RoadEdge::getToNodeId, toNodeId)
                .one();
        if (edge == null) {
            edge = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, toNodeId)
                    .eq(RoadEdge::getToNodeId, fromNodeId)
                    .one();
        }
        if (edge == null) return null;

        return switch (transportMode != null ? transportMode : 1) {
            case 1 -> edge.getWalkTime();
            case 2 -> edge.getBikeTime();
            case 3 -> edge.getShuttleTime();
            default -> edge.getWalkTime();
        };
    }

    @Override
    public Long getFacilityNodeId(Long facilityId) {
        if (facilityId == null) return null;
        RoadNode node = roadNodeService.lambdaQuery()
                .eq(RoadNode::getFacilityId, facilityId)
                .one();
        return node != null ? node.getId() : null;
    }

    @Override
    public Long findNearestNodeByCoords(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) return null;

        if (graph.isLoaded()) {
            int idx = graph.findNearestIndex(lat.doubleValue(), lng.doubleValue());
            return idx >= 0 ? graph.getNodeId(idx) : null;
        }

        // DB fallback
        List<RoadNode> allNodes = roadNodeService.lambdaQuery()
                .eq(RoadNode::getEnabled, true)
                .list();
        if (allNodes == null || allNodes.isEmpty()) return null;

        Long nearestId = null;
        double minDist = Double.MAX_VALUE;
        for (RoadNode node : allNodes) {
            if (node.getLatitude() == null || node.getLongitude() == null) continue;
            double d = haversineMeters(
                lat.doubleValue(), lng.doubleValue(),
                node.getLatitude().doubleValue(), node.getLongitude().doubleValue()
            );
            if (d < minDist) {
                minDist = d;
                nearestId = node.getId();
            }
        }
        return nearestId;
    }

    // ==================== 内存图 Dijkstra（核心优化） ====================

    /**
     * 基于 {@link InMemoryGraph} 的 Dijkstra 最短路径算法。
     * 使用原始类型数组替代 HashMap，完全消除 DB 查询。
     */
    private PathResult dijkstra(Long startNodeId, Long endNodeId, Integer transportMode, String strategy) {
        int startIdx = graph.getNodeIndex(startNodeId);
        int endIdx = graph.getNodeIndex(endNodeId);
        if (startIdx < 0 || endIdx < 0) {
            log.warn("起点/终点不在内存图中: start={}, end={}", startNodeId, endNodeId);
            return null;
        }

        int n = graph.getNodeCount();
        float[] dist = new float[n];
        float[] actualDist = new float[n];
        float[] actualTime = new float[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);

        dist[startIdx] = 0;
        actualDist[startIdx] = 0;
        actualTime[startIdx] = 0;

        PriorityQueue<IntWeight> pq = new PriorityQueue<>(
                Comparator.comparingDouble(iw -> iw.weight));
        pq.add(new IntWeight(startIdx, 0));

        while (!pq.isEmpty()) {
            IntWeight cur = pq.poll();
            int u = cur.nodeIdx;
            if (visited[u]) continue;
            visited[u] = true;

            if (u == endIdx) break;

            graph.forEachEdge(u, (v, edgeDist, transportType, walkTime, bikeTime, shuttleTime, congestion, isReverse) -> {
                if (visited[v]) return;
                if (!isAccessible(transportType, transportMode)) return;

                float edgeWeight = computeEdgeWeight(edgeDist, walkTime, bikeTime, shuttleTime,
                        congestion, strategy, transportMode);
                float newDist = dist[u] + edgeWeight;
                if (newDist < dist[v]) {
                    dist[v] = newDist;
                    prev[v] = u;
                    actualDist[v] = actualDist[u] + edgeDist;
                    actualTime[v] = actualTime[u] + getTimeForMode(walkTime, bikeTime, shuttleTime, transportMode);
                    pq.add(new IntWeight(v, newDist));
                }
            });
        }

        if (prev[endIdx] < 0 && startIdx != endIdx) {
            log.warn("无法找到从 {} 到 {} 的路径", startNodeId, endNodeId);
            return null;
        }

        List<PathNode> pathNodes = rebuildPathInMemory(startIdx, endIdx, prev, startNodeId, endNodeId);
        return new PathResult(pathNodes, actualDist[endIdx], actualTime[endIdx]);
    }

    /**
     * 基于 {@link InMemoryGraph} 的 A* 算法。
     * 使用 Haversine 直线距离作为可采纳启发式。
     */
    private PathResult aStar(Long startNodeId, Long endNodeId, Integer transportMode, String strategy) {
        int startIdx = graph.getNodeIndex(startNodeId);
        int endIdx = graph.getNodeIndex(endNodeId);
        if (startIdx < 0 || endIdx < 0) return null;

        double endLat = graph.getNodeLat(endIdx);
        double endLng = graph.getNodeLng(endIdx);

        int n = graph.getNodeCount();
        float[] gScore = new float[n];
        float[] actualDist = new float[n];
        float[] actualTime = new float[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(gScore, Float.MAX_VALUE);
        Arrays.fill(prev, -1);

        gScore[startIdx] = 0;
        actualDist[startIdx] = 0;
        actualTime[startIdx] = 0;

        float hStart = (float) haversineMeters(
                graph.getNodeLat(startIdx), graph.getNodeLng(startIdx), endLat, endLng);
        PriorityQueue<IntWeight> pq = new PriorityQueue<>(
                Comparator.comparingDouble(iw -> iw.weight));
        pq.add(new IntWeight(startIdx, hStart));

        while (!pq.isEmpty()) {
            IntWeight cur = pq.poll();
            int u = cur.nodeIdx;
            if (visited[u]) continue;
            visited[u] = true;

            if (u == endIdx) break;

            graph.forEachEdge(u, (v, edgeDist, transportType, walkTime, bikeTime, shuttleTime, congestion, isReverse) -> {
                if (visited[v]) return;
                if (!isAccessible(transportType, transportMode)) return;

                float edgeWeight = computeEdgeWeight(edgeDist, walkTime, bikeTime, shuttleTime,
                        congestion, strategy, transportMode);
                float tentativeG = gScore[u] + edgeWeight;
                if (tentativeG < gScore[v]) {
                    gScore[v] = tentativeG;
                    prev[v] = u;
                    actualDist[v] = actualDist[u] + edgeDist;
                    actualTime[v] = actualTime[u] + getTimeForMode(walkTime, bikeTime, shuttleTime, transportMode);
                    float h = (float) haversineMeters(
                            graph.getNodeLat(v), graph.getNodeLng(v), endLat, endLng);
                    pq.add(new IntWeight(v, tentativeG + h));
                }
            });
        }

        if (prev[endIdx] < 0 && startIdx != endIdx) {
            log.warn("A*无法找到从 {} 到 {} 的路径", startNodeId, endNodeId);
            return null;
        }

        List<PathNode> pathNodes = rebuildPathInMemory(startIdx, endIdx, prev, startNodeId, endNodeId);
        return new PathResult(pathNodes, actualDist[endIdx], actualTime[endIdx]);
    }

    /**
     * 基于 {@link InMemoryGraph} 的多目标 Dijkstra。
     * 从起点出发，命中任意目标即停止。
     */
    private PathResult dijkstraToTargets(Long startNodeId, Set<Long> targetSet,
                                          Integer transportMode, String strategy) {
        int startIdx = graph.getNodeIndex(startNodeId);
        if (startIdx < 0) return null;

        // Convert targetSet to index set
        int[] targetIndices = new int[targetSet.size()];
        int ti = 0;
        boolean startIsTarget = false;
        for (Long tid : targetSet) {
            int idx = graph.getNodeIndex(tid);
            if (idx >= 0) {
                targetIndices[ti++] = idx;
                if (idx == startIdx) startIsTarget = true;
            }
        }
        if (ti == 0) return null;
        if (ti < targetIndices.length) {
            targetIndices = Arrays.copyOf(targetIndices, ti);
        }

        // Quick check: if start is itself a target
        if (startIsTarget) {
            List<PathNode> singleNode = new ArrayList<>();
            PathNode pn = buildPathNode(startIdx, startNodeId, startNodeId);
            singleNode.add(pn);
            return new PathResult(singleNode, 0, 0);
        }

        int n = graph.getNodeCount();
        float[] dist = new float[n];
        float[] actualDist = new float[n];
        float[] actualTime = new float[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);

        dist[startIdx] = 0;
        actualDist[startIdx] = 0;
        actualTime[startIdx] = 0;

        PriorityQueue<IntWeight> pq = new PriorityQueue<>(
                Comparator.comparingDouble(iw -> iw.weight));
        pq.add(new IntWeight(startIdx, 0));

        int reachedIdx = -1;
        boolean[] isTarget = new boolean[n];
        for (int idx : targetIndices) {
            isTarget[idx] = true;
        }

        while (!pq.isEmpty()) {
            IntWeight cur = pq.poll();
            int u = cur.nodeIdx;
            if (visited[u]) continue;
            visited[u] = true;

            if (isTarget[u]) {
                reachedIdx = u;
                break;
            }

            graph.forEachEdge(u, (v, edgeDist, transportType, walkTime, bikeTime, shuttleTime, congestion, isReverse) -> {
                if (visited[v]) return;
                if (!isAccessible(transportType, transportMode)) return;

                float edgeWeight = computeEdgeWeight(edgeDist, walkTime, bikeTime, shuttleTime,
                        congestion, strategy, transportMode);
                float newDist = dist[u] + edgeWeight;
                if (newDist < dist[v]) {
                    dist[v] = newDist;
                    prev[v] = u;
                    actualDist[v] = actualDist[u] + edgeDist;
                    actualTime[v] = actualTime[u] + getTimeForMode(walkTime, bikeTime, shuttleTime, transportMode);
                    pq.add(new IntWeight(v, newDist));
                }
            });
        }

        if (reachedIdx < 0) {
            log.warn("多目标Dijkstra未找到路径: 起点={}, 目标数={}", startNodeId, targetSet.size());
            return null;
        }

        Long reachedNodeId = graph.getNodeId(reachedIdx);
        List<PathNode> pathNodes = rebuildPathInMemory(startIdx, reachedIdx, prev, startNodeId, reachedNodeId);
        return new PathResult(pathNodes, actualDist[reachedIdx], actualTime[reachedIdx]);
    }

    /**
     * 基于 {@link InMemoryGraph} 的多源多目标 Dijkstra。
     * 起点和终点均为集合，命中任意目标即停止。
     */
    private PathResult dijkstraMultiSourceToTargets(
            Set<Long> startNodeIds, Set<Long> targetSet,
            Integer transportMode, String strategy) {

        int n = graph.getNodeCount();
        float[] dist = new float[n];
        float[] actualDist = new float[n];
        float[] actualTime = new float[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];

        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);

        // Initialize multi-source
        boolean[] isTarget = new boolean[n];
        for (Long tid : targetSet) {
            int idx = graph.getNodeIndex(tid);
            if (idx >= 0) isTarget[idx] = true;
        }

        PriorityQueue<IntWeight> pq = new PriorityQueue<>(
                Comparator.comparingDouble(iw -> iw.weight));

        boolean startIsTarget = false;
        for (Long sid : startNodeIds) {
            int idx = graph.getNodeIndex(sid);
            if (idx < 0) continue;
            dist[idx] = 0;
            actualDist[idx] = 0;
            actualTime[idx] = 0;
            pq.add(new IntWeight(idx, 0));
            if (isTarget[idx]) {
                startIsTarget = true;
                // Still need to init all starts, but we can short-circuit later
            }
        }

        // Quick check: if any start node IS a target, return it with minimal distance
        // (pick any — they're all distance 0 from themselves)
        if (startIsTarget) {
            for (Long sid : startNodeIds) {
                int idx = graph.getNodeIndex(sid);
                if (idx >= 0 && isTarget[idx]) {
                    List<PathNode> singleNode = new ArrayList<>();
                    PathNode pn = buildPathNode(idx, sid, sid);
                    singleNode.add(pn);
                    return new PathResult(singleNode, 0, 0);
                }
            }
        }

        int reachedIdx = -1;

        while (!pq.isEmpty()) {
            IntWeight cur = pq.poll();
            int u = cur.nodeIdx;
            if (visited[u]) continue;
            visited[u] = true;

            if (isTarget[u]) {
                reachedIdx = u;
                break;
            }

            graph.forEachEdge(u, (v, edgeDist, transportType, walkTime, bikeTime, shuttleTime, congestion, isReverse) -> {
                if (visited[v]) return;
                if (!isAccessible(transportType, transportMode)) return;

                float edgeWeight = computeEdgeWeight(edgeDist, walkTime, bikeTime, shuttleTime,
                        congestion, strategy, transportMode);
                float newDist = dist[u] + edgeWeight;
                if (newDist < dist[v]) {
                    dist[v] = newDist;
                    prev[v] = u;
                    actualDist[v] = actualDist[u] + edgeDist;
                    actualTime[v] = actualTime[u] + getTimeForMode(walkTime, bikeTime, shuttleTime, transportMode);
                    pq.add(new IntWeight(v, newDist));
                }
            });
        }

        if (reachedIdx < 0) {
            log.warn("多源Dijkstra未找到路径: 起点数={}, 目标数={}", startNodeIds.size(), targetSet.size());
            return null;
        }

        // Trace back to find actual start
        int actualStartIdx = reachedIdx;
        while (prev[actualStartIdx] >= 0) {
            actualStartIdx = prev[actualStartIdx];
        }

        Long actualStartId = graph.getNodeId(actualStartIdx);
        Long reachedNodeId = graph.getNodeId(reachedIdx);
        List<PathNode> pathNodes = rebuildPathInMemory(actualStartIdx, reachedIdx, prev, actualStartId, reachedNodeId);
        return new PathResult(pathNodes, actualDist[reachedIdx], actualTime[reachedIdx]);
    }

    // ==================== 路径重建 ====================

    /**
     * 从 predecessor 数组重建路径节点列表（使用内存图，无 DB 查询）。
     */
    private List<PathNode> rebuildPathInMemory(int startIdx, int endIdx, int[] prev,
                                                Long startNodeId, Long endNodeId) {
        List<Integer> idxList = new ArrayList<>();
        int cur = endIdx;
        while (cur >= 0) {
            idxList.add(cur);
            if (cur == startIdx) break;
            cur = prev[cur];
        }
        Collections.reverse(idxList);

        List<PathNode> pathNodes = new ArrayList<>();
        int sequence = 0;
        for (int idx : idxList) {
            Long nodeId = graph.getNodeId(idx);
            PathNode pn = buildPathNode(idx, nodeId, nodeId.equals(endNodeId) ? endNodeId : startNodeId);
            pn.setSequence(sequence++);
            if (nodeId.equals(startNodeId)) {
                pn.setAction("start");
            } else if (nodeId.equals(endNodeId)) {
                pn.setAction("end");
            } else {
                pn.setAction("visit");
            }
            pathNodes.add(pn);
        }
        return pathNodes;
    }

    /** 从内存图数据构建单个 PathNode */
    private PathNode buildPathNode(int nodeIdx, Long nodeId, Long endNodeId) {
        PathNode pn = new PathNode();
        pn.setNodeId(nodeId);
        RoadNode node = graph.getNodeByIndex(nodeIdx);
        if (node != null) {
            pn.setName(node.getName());
            pn.setLatitude(node.getLatitude());
            pn.setLongitude(node.getLongitude());
            pn.setIsPrimary(node.getIsPrimary());
            pn.setScenicAreaId(node.getScenicAreaId());
            pn.setScenicAreaName(null);
        }
        return pn;
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算边权（根据策略和交通方式）。
     * 所有数据已由 forEachEdge 提供，无需 DB 查询。
     */
    private float computeEdgeWeight(float distance, int walkTime, int bikeTime, int shuttleTime,
                                     float congestion, String strategy, Integer transportMode) {
        String effective = (strategy != null) ? strategy : "shortest_distance";
        return switch (effective) {
            case "shortest_time" -> {
                int time = switch (transportMode != null ? transportMode : 1) {
                    case 2 -> bikeTime > 0 ? bikeTime : Integer.MAX_VALUE / 2;
                    case 3 -> shuttleTime > 0 ? shuttleTime : Integer.MAX_VALUE / 2;
                    default -> walkTime > 0 ? walkTime : Integer.MAX_VALUE / 2;
                };
                yield time;
            }
            case "avoid_crowd" -> distance * (1.0f + congestion);
            default -> distance;
        };
    }

    /** 获取当前交通方式对应的通行时间 */
    private int getTimeForMode(int walkTime, int bikeTime, int shuttleTime, Integer transportMode) {
        return switch (transportMode != null ? transportMode : 1) {
            case 2 -> bikeTime;
            case 3 -> shuttleTime;
            default -> walkTime;
        };
    }

    /**
     * 检查边是否可通过指定交通方式通行。
     * @param edgeTransportType 边的通行方式编码
     * @param transportMode 用户选择的交通方式（1=步行,2=骑行,3=驾驶）
     */
    private boolean isAccessible(int edgeTransportType, Integer transportMode) {
        if (edgeTransportType == 0 || edgeTransportType == 5) return true;
        int mode = transportMode != null ? transportMode : 1;
        return switch (mode) {
            case 1 -> edgeTransportType == 1 || edgeTransportType == 4;
            case 2 -> edgeTransportType == 2 || edgeTransportType == 4;
            case 3 -> edgeTransportType == 3;
            default -> true;
        };
    }

    /**
     * 找到最近的节点（使用 Dijkstra 计算最短路径距离）
     */
    private Long findNearestNode(Long fromNodeId, List<Long> targetNodes,
                                  Integer transportMode, String strategy) {
        Long nearest = null;
        float minDist = Float.MAX_VALUE;

        for (Long targetId : targetNodes) {
            if (graph.isLoaded()) {
                PathResult pr = dijkstra(fromNodeId, targetId, transportMode, strategy);
                if (pr != null && pr.totalDistance < minDist) {
                    minDist = pr.totalDistance;
                    nearest = targetId;
                }
            } else {
                List<PathNode> path = dijkstraDb(fromNodeId, targetId, transportMode, strategy);
                if (path != null && !path.isEmpty()) {
                    BigDecimal dist = calculatePathDistanceDb(path);
                    if (dist != null && dist.floatValue() < minDist) {
                        minDist = dist.floatValue();
                        nearest = targetId;
                    }
                }
            }
        }
        return nearest;
    }

    /** DB 降级：计算路径总距离（使用 getDistanceBetweenNodes DB 查询） */
    private BigDecimal calculatePathDistanceDb(List<PathNode> path) {
        if (path == null || path.size() < 2) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < path.size() - 1; i++) {
            BigDecimal dist = getDistanceBetweenNodes(path.get(i).getNodeId(), path.get(i + 1).getNodeId());
            if (dist != null) total = total.add(dist);
        }
        return total;
    }

    // ==================== 数学工具 ====================

    /**
     * Haversine 公式计算两点间直线距离（米）
     */
    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    /**
     * 获取交通方式名称
     */
    private String getTransportModeName(Integer transportMode) {
        return switch (transportMode != null ? transportMode : 1) {
            case 1 -> "walk";
            case 2 -> "bike";
            case 3 -> "shuttle";
            default -> "walk";
        };
    }

    // ==================== DB 降级方法（图未加载时使用） ====================

    /** DB 降级：Dijkstra 算法（与原实现相同） */
    private List<PathNode> dijkstraDb(Long startNodeId, Long endNodeId,
                                       Integer transportMode, String strategy) {
        Map<Long, Long> predecessor = new HashMap<>();
        Map<Long, BigDecimal> weight = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        PriorityQueue<NodeWeight> pq = new PriorityQueue<>(Comparator.comparing(nw -> nw.weight));

        weight.put(startNodeId, BigDecimal.ZERO);
        pq.add(new NodeWeight(startNodeId, BigDecimal.ZERO));

        while (!pq.isEmpty()) {
            NodeWeight current = pq.poll();
            Long currentNodeId = current.nodeId;

            if (settled.contains(currentNodeId)) continue;
            settled.add(currentNodeId);

            if (currentNodeId.equals(endNodeId)) break;

            List<RoadEdge> edges = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, currentNodeId)
                    .or(w -> w.eq(RoadEdge::getToNodeId, currentNodeId)
                               .eq(RoadEdge::getBidirectional, 1))
                    .list();

            for (RoadEdge edge : edges) {
                boolean isReverse = edge.getToNodeId().equals(currentNodeId);
                Long neighborId = isReverse ? edge.getFromNodeId() : edge.getToNodeId();
                if (settled.contains(neighborId)) continue;
                if (!isTransportable(edge, transportMode)) continue;

                BigDecimal edgeWeight = getEdgeWeightFromEdge(edge, strategy, transportMode);
                BigDecimal newWeight = weight.get(currentNodeId).add(edgeWeight);

                BigDecimal defaultMax = BigDecimal.valueOf(Double.MAX_VALUE);
                if (newWeight.compareTo(weight.getOrDefault(neighborId, defaultMax)) < 0) {
                    weight.put(neighborId, newWeight);
                    predecessor.put(neighborId, currentNodeId);
                    pq.add(new NodeWeight(neighborId, newWeight));
                }
            }
        }

        if (!predecessor.containsKey(endNodeId) && !endNodeId.equals(startNodeId)) {
            log.warn("DB降级: 无法找到从 {} 到 {} 的路径", startNodeId, endNodeId);
            return null;
        }
        return rebuildPathDb(startNodeId, endNodeId, predecessor);
    }

    /** DB 降级：A* 算法 */
    private List<PathNode> aStarDb(Long startNodeId, Long endNodeId,
                                    Integer transportMode, String strategy) {
        RoadNode endNode = roadNodeService.getById(endNodeId);
        if (endNode == null) return null;

        Map<Long, Long> predecessor = new HashMap<>();
        Map<Long, BigDecimal> gScore = new HashMap<>();
        Set<Long> settled = new HashSet<>();

        PriorityQueue<AStarNode> pq = new PriorityQueue<>(
            Comparator.comparing(an -> an.fScore));

        gScore.put(startNodeId, BigDecimal.ZERO);
        BigDecimal hStart = calculateHaversineDb(startNodeId, endNode);
        pq.add(new AStarNode(startNodeId, BigDecimal.ZERO, hStart));

        while (!pq.isEmpty()) {
            AStarNode current = pq.poll();
            Long currentNodeId = current.nodeId;

            if (settled.contains(currentNodeId)) continue;
            settled.add(currentNodeId);

            if (currentNodeId.equals(endNodeId)) break;

            List<RoadEdge> edges = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, currentNodeId)
                    .or(w -> w.eq(RoadEdge::getToNodeId, currentNodeId)
                               .eq(RoadEdge::getBidirectional, 1))
                    .list();

            for (RoadEdge edge : edges) {
                boolean isReverse = edge.getToNodeId().equals(currentNodeId);
                Long neighborId = isReverse ? edge.getFromNodeId() : edge.getToNodeId();
                if (settled.contains(neighborId)) continue;
                if (!isTransportable(edge, transportMode)) continue;

                BigDecimal edgeWeight = getEdgeWeightFromEdge(edge, strategy, transportMode);
                BigDecimal tentativeG = gScore.get(currentNodeId).add(edgeWeight);

                BigDecimal defaultMax = BigDecimal.valueOf(Double.MAX_VALUE);
                if (tentativeG.compareTo(gScore.getOrDefault(neighborId, defaultMax)) < 0) {
                    gScore.put(neighborId, tentativeG);
                    predecessor.put(neighborId, currentNodeId);
                    BigDecimal hValue = calculateHaversineDb(neighborId, endNode);
                    pq.add(new AStarNode(neighborId, tentativeG, hValue));
                }
            }
        }

        if (!predecessor.containsKey(endNodeId) && !endNodeId.equals(startNodeId)) {
            log.warn("DB降级: A*无法找到从 {} 到 {} 的路径", startNodeId, endNodeId);
            return null;
        }
        return rebuildPathDb(startNodeId, endNodeId, predecessor);
    }

    /** DB 降级：多目标 Dijkstra */
    private List<PathNode> dijkstraToTargetsDb(Long startNodeId, Set<Long> targetSet,
                                                Integer transportMode, String strategy) {
        Map<Long, Long> predecessor = new HashMap<>();
        Map<Long, BigDecimal> weight = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        PriorityQueue<NodeWeight> pq = new PriorityQueue<>(Comparator.comparing(nw -> nw.weight));

        weight.put(startNodeId, BigDecimal.ZERO);
        pq.add(new NodeWeight(startNodeId, BigDecimal.ZERO));

        Long reachedTarget = null;

        while (!pq.isEmpty()) {
            NodeWeight current = pq.poll();
            Long currentNodeId = current.nodeId;
            if (settled.contains(currentNodeId)) continue;
            settled.add(currentNodeId);

            if (targetSet.contains(currentNodeId)) {
                reachedTarget = currentNodeId;
                break;
            }

            List<RoadEdge> edges = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, currentNodeId)
                    .or(w -> w.eq(RoadEdge::getToNodeId, currentNodeId)
                               .eq(RoadEdge::getBidirectional, 1))
                    .list();

            for (RoadEdge edge : edges) {
                boolean isReverse = edge.getToNodeId().equals(currentNodeId);
                Long neighborId = isReverse ? edge.getFromNodeId() : edge.getToNodeId();
                if (settled.contains(neighborId)) continue;
                if (!isTransportable(edge, transportMode)) continue;

                BigDecimal edgeWeight = getEdgeWeightFromEdge(edge, strategy, transportMode);
                BigDecimal newWeight = weight.get(currentNodeId).add(edgeWeight);

                BigDecimal defaultMax = BigDecimal.valueOf(Double.MAX_VALUE);
                if (newWeight.compareTo(weight.getOrDefault(neighborId, defaultMax)) < 0) {
                    weight.put(neighborId, newWeight);
                    predecessor.put(neighborId, currentNodeId);
                    pq.add(new NodeWeight(neighborId, newWeight));
                }
            }
        }

        if (reachedTarget == null) return null;
        if (!predecessor.containsKey(reachedTarget) && !reachedTarget.equals(startNodeId)) return null;
        return rebuildPathDb(startNodeId, reachedTarget, predecessor);
    }

    /** DB 降级：多源多目标 Dijkstra */
    private List<PathNode> dijkstraMultiSourceToTargetsDb(
            Set<Long> startNodeIds, Set<Long> targetSet,
            Integer transportMode, String strategy) {

        Map<Long, Long> predecessor = new HashMap<>();
        Map<Long, BigDecimal> weight = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        PriorityQueue<NodeWeight> pq = new PriorityQueue<>(Comparator.comparing(nw -> nw.weight));

        for (Long startId : startNodeIds) {
            weight.put(startId, BigDecimal.ZERO);
            pq.add(new NodeWeight(startId, BigDecimal.ZERO));
        }

        Long reachedTarget = null;

        while (!pq.isEmpty()) {
            NodeWeight current = pq.poll();
            Long currentNodeId = current.nodeId;
            if (settled.contains(currentNodeId)) continue;
            settled.add(currentNodeId);

            if (targetSet.contains(currentNodeId)) {
                reachedTarget = currentNodeId;
                break;
            }

            List<RoadEdge> edges = roadEdgeService.lambdaQuery()
                    .eq(RoadEdge::getFromNodeId, currentNodeId)
                    .or(w -> w.eq(RoadEdge::getToNodeId, currentNodeId)
                               .eq(RoadEdge::getBidirectional, 1))
                    .list();

            for (RoadEdge edge : edges) {
                boolean isReverse = edge.getToNodeId().equals(currentNodeId);
                Long neighborId = isReverse ? edge.getFromNodeId() : edge.getToNodeId();
                if (settled.contains(neighborId)) continue;
                if (!isTransportable(edge, transportMode)) continue;

                BigDecimal edgeWeight = getEdgeWeightFromEdge(edge, strategy, transportMode);
                BigDecimal newWeight = weight.get(currentNodeId).add(edgeWeight);

                BigDecimal defaultMax = BigDecimal.valueOf(Double.MAX_VALUE);
                if (newWeight.compareTo(weight.getOrDefault(neighborId, defaultMax)) < 0) {
                    weight.put(neighborId, newWeight);
                    predecessor.put(neighborId, currentNodeId);
                    pq.add(new NodeWeight(neighborId, newWeight));
                }
            }
        }

        if (reachedTarget == null) return null;

        Long actualStart = reachedTarget;
        while (predecessor.containsKey(actualStart)) {
            actualStart = predecessor.get(actualStart);
        }
        return rebuildPathDb(actualStart, reachedTarget, predecessor);
    }

    /** DB 降级：重建路径 */
    private List<PathNode> rebuildPathDb(Long startNodeId, Long endNodeId, Map<Long, Long> predecessor) {
        List<Long> nodeIds = new ArrayList<>();
        Long current = endNodeId;
        while (current != null) {
            nodeIds.add(current);
            current = predecessor.get(current);
        }
        Collections.reverse(nodeIds);

        List<PathNode> pathNodes = new ArrayList<>();
        int sequence = 0;
        for (Long nodeId : nodeIds) {
            RoadNode node = roadNodeService.getById(nodeId);
            PathNode pn = new PathNode();
            pn.setNodeId(nodeId);
            pn.setSequence(sequence++);
            if (node != null) {
                pn.setName(node.getName());
                pn.setLatitude(node.getLatitude());
                pn.setLongitude(node.getLongitude());
                pn.setIsPrimary(node.getIsPrimary());
                pn.setScenicAreaId(node.getScenicAreaId());
            pn.setScenicAreaName(node.getScenicAreaName());  // from RoadNode transient field
            }
            if (nodeId.equals(startNodeId)) {
                pn.setAction("start");
            } else if (nodeId.equals(endNodeId)) {
                pn.setAction("end");
            } else {
                pn.setAction("visit");
            }
            pathNodes.add(pn);
        }
        return pathNodes;
    }

    // ==================== DB 降级辅助方法 ====================

    private boolean isTransportable(RoadEdge edge, Integer transportMode) {
        if (edge == null) return false;
        Integer transportType = edge.getTransportType();
        if (transportType == null || transportType == 0 || transportType == 5) return true;

        return switch (transportMode != null ? transportMode : 1) {
            case 1 -> transportType == 1 || transportType == 4 || transportType == 5;
            case 2 -> transportType == 2 || transportType == 4 || transportType == 5;
            case 3 -> transportType == 3 || transportType == 5;
            default -> true;
        };
    }

    private BigDecimal getEdgeWeightFromEdge(RoadEdge edge, String strategy, Integer transportMode) {
        if (edge == null) return BigDecimal.valueOf(Double.MAX_VALUE);
        String effectiveStrategy = (strategy != null) ? strategy : "shortest_distance";

        return switch (effectiveStrategy) {
            case "shortest_time" -> {
                int time = switch (transportMode != null ? transportMode : 1) {
                    case 1 -> edge.getWalkTime() != null ? edge.getWalkTime() : Integer.MAX_VALUE / 2;
                    case 2 -> edge.getBikeTime() != null ? edge.getBikeTime() : Integer.MAX_VALUE / 2;
                    case 3 -> edge.getShuttleTime() != null ? edge.getShuttleTime() : Integer.MAX_VALUE / 2;
                    default -> edge.getWalkTime() != null ? edge.getWalkTime() : Integer.MAX_VALUE / 2;
                };
                yield BigDecimal.valueOf(time);
            }
            case "avoid_crowd" -> {
                double congestion = edge.getCurrentCongestion() != null
                        ? edge.getCurrentCongestion().doubleValue() : 0.0;
                double penalty = 1.0 + congestion;
                yield edge.getDistance().multiply(BigDecimal.valueOf(penalty));
            }
            default -> edge.getDistance();
        };
    }

    private BigDecimal calculateHaversineDb(Long nodeId, RoadNode targetNode) {
        RoadNode node = roadNodeService.getById(nodeId);
        if (node == null || node.getLatitude() == null || node.getLongitude() == null
                || targetNode.getLatitude() == null || targetNode.getLongitude() == null) {
            return BigDecimal.ZERO;
        }
        double d = haversineMeters(
            node.getLatitude().doubleValue(), node.getLongitude().doubleValue(),
            targetNode.getLatitude().doubleValue(), targetNode.getLongitude().doubleValue()
        );
        return BigDecimal.valueOf(d);
    }

    // ==================== 内部类 ====================

    /** Dijkstra/A* 算法结果 */
    private static class PathResult {
        final List<PathNode> nodes;
        final float totalDistance;
        final float totalTime;

        PathResult(List<PathNode> nodes, float totalDistance, float totalTime) {
            this.nodes = nodes;
            this.totalDistance = totalDistance;
            this.totalTime = totalTime;
        }
    }

    /** 优先队列节点（权重不限，用于 Dijkstra 和 A*） */
    private static class IntWeight {
        final int nodeIdx;
        final double weight;

        IntWeight(int nodeIdx, double weight) {
            this.nodeIdx = nodeIdx;
            this.weight = weight;
        }
    }

    /** DB 降级：节点权重对 */
    private static class NodeWeight {
        Long nodeId;
        BigDecimal weight;
        NodeWeight(Long nodeId, BigDecimal weight) {
            this.nodeId = nodeId;
            this.weight = weight;
        }
    }

    /** DB 降级：A* 节点 */
    private static class AStarNode {
        Long nodeId;
        BigDecimal fScore;
        AStarNode(Long nodeId, BigDecimal gScore, BigDecimal hScore) {
            this.nodeId = nodeId;
            this.fScore = gScore.add(hScore);
        }
    }
}
