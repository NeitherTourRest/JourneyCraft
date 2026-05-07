-- ============================================================
-- JourneyCraft 数据库操作：添加昌平区景区 + 正确关联路网节点
-- ============================================================

-- Step 1: 插入新的景区数据
-- ============================================================
INSERT INTO t_scenic_area (id, node_id, name, type, city, latitude, longitude, description, rating, heat_score, ticket_price, status)
VALUES
  (6, NULL, '北京邮电大学沙河校区', 1, '北京', 40.1570, 116.2830, '北京邮电大学昌平沙河校区，信息黄埔', 4.5, 850, 0, 1),
  (7, NULL, '居庸关长城', 0, '北京', 40.2880, 116.0680, '天下第一雄关，万里长城重要关隘', 4.6, 3200, 40, 1),
  (8, NULL, '明十三陵', 0, '北京', 40.2510, 116.2220, '明朝十三位皇帝陵墓群，世界文化遗产', 4.7, 5800, 60, 1),
  (9, NULL, '昌平新城滨河森林公园', 0, '北京', 40.2180, 116.2390, '昌平新城大型城市森林公园', 4.3, 1500, 0, 1),
  (10, NULL, '温都水城', 0, '北京', 40.1150, 116.3580, '大型温泉休闲度假区', 4.2, 980, 120, 1);

-- 同步更新 t_temp_scenic_area (导航模块外键目标表)
INSERT INTO t_temp_scenic_area (id, name, type, city, latitude, longitude, description)
SELECT id, name, type, city, latitude, longitude, description
FROM t_scenic_area WHERE id >= 6;

-- ============================================================
-- Step 2: 正确关联路网节点——每个景区只关联周边节点
-- ============================================================
-- 先清除所有现有关联（保留以备重新计算）
-- 注意：这不会删除节点，只会把 scenic_area_id 设为 NULL
UPDATE t_navigation_road_node SET scenic_area_id = NULL, is_primary = 0;

-- 为每个景区关联半径内的节点
-- 使用 Haversine 公式计算距离，只关联 OSM 节点

-- 景区1: 故宫博物院 (39.9163, 116.3971) — 位于北京市中心，不在昌平OSM范围内
-- 只关联非常近的节点
UPDATE t_navigation_road_node 
SET scenic_area_id = 1 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 39.9163, 116.3971) <= 500;

-- 景区2: 虎峪风景区 (40.2710, 116.1430)
UPDATE t_navigation_road_node 
SET scenic_area_id = 2 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2710, 116.1430) <= 800;

-- 景区3: 凤山温泉度假村 (40.2370, 116.2640)
UPDATE t_navigation_road_node 
SET scenic_area_id = 3 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2370, 116.2640) <= 600;

-- 景区4: 昌平公园 (40.2200, 116.2280)
UPDATE t_navigation_road_node 
SET scenic_area_id = 4 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2200, 116.2280) <= 600;

-- 景区5: 蟒山国家森林公园 (40.2550, 116.2750)
UPDATE t_navigation_road_node 
SET scenic_area_id = 5 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2550, 116.2750) <= 800;

-- 景区6: 北京邮电大学沙河校区 (40.1570, 116.2830)
UPDATE t_navigation_road_node 
SET scenic_area_id = 6 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.1570, 116.2830) <= 400;

-- 景区7: 居庸关长城 (40.2880, 116.0680)
UPDATE t_navigation_road_node 
SET scenic_area_id = 7 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2880, 116.0680) <= 1000;

-- 景区8: 明十三陵 (40.2510, 116.2220)
UPDATE t_navigation_road_node 
SET scenic_area_id = 8 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2510, 116.2220) <= 1000;

-- 景区9: 昌平新城滨河森林公园 (40.2180, 116.2390)
UPDATE t_navigation_road_node 
SET scenic_area_id = 9 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.2180, 116.2390) <= 600;

-- 景区10: 温都水城 (40.1150, 116.3580)
UPDATE t_navigation_road_node 
SET scenic_area_id = 10 
WHERE scenic_area_id IS NULL
  AND enabled = 1 AND deleted = 0
  AND calculate_distance(latitude, longitude, 40.1150, 116.3580) <= 600;

-- ============================================================
-- Step 3: 为每个景区设置 is_primary 代表节点
-- 优先选择 POI(type=2) 或入口(type=0) 节点
-- ============================================================
UPDATE t_navigation_road_node n1
JOIN (
  SELECT n2.scenic_area_id, MIN(n2.id) as min_id
  FROM t_navigation_road_node n2
  WHERE n2.scenic_area_id IS NOT NULL AND n2.enabled = 1
  GROUP BY n2.scenic_area_id
) t ON n1.scenic_area_id = t.scenic_area_id AND n1.id = t.min_id
SET n1.is_primary = 1;

-- 如果有 POI 或入口节点，升级它们为 primary
UPDATE t_navigation_road_node n1
JOIN (
  SELECT n2.scenic_area_id, MIN(n2.id) as best_id
  FROM t_navigation_road_node n2
  WHERE n2.scenic_area_id IS NOT NULL 
    AND n2.enabled = 1
    AND n2.node_type IN (0, 2)  -- 入口或POI
  GROUP BY n2.scenic_area_id
) t ON n1.scenic_area_id = t.scenic_area_id AND n1.id = t.best_id
SET n1.is_primary = 1;

-- ============================================================
-- Step 4: 验证结果
-- ============================================================
SELECT '=== 景区列表 ===' as info;
SELECT id, name, type, latitude, longitude FROM t_scenic_area ORDER BY id;

SELECT '=== 每景区节点数 ===' as info;
SELECT scenic_area_id, COUNT(*) as node_count, 
       SUM(CASE WHEN node_type = 0 THEN 1 ELSE 0 END) as entrances,
       SUM(CASE WHEN node_type = 2 THEN 1 ELSE 0 END) as pois,
       SUM(CASE WHEN is_primary = 1 THEN 1 ELSE 0 END) as has_primary
FROM t_navigation_road_node 
WHERE scenic_area_id IS NOT NULL 
GROUP BY scenic_area_id 
ORDER BY scenic_area_id;
