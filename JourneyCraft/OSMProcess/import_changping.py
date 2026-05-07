#!/usr/bin/env python3
# =============================================
# JourneyCraft - 昌平区 OSM PBF 数据导入脚本 v2
# 输入: Changping.osm.pbf
# 输出: MySQL journeycraft.t_navigation_road_node / t_navigation_road_edge
# =============================================

import osmium
import mysql.connector
from mysql.connector import Error
import json
import math
import sys
import os
from datetime import datetime

# ========== 配置 ==========
MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root123",
    "database": "journeycraft",
    "charset": "utf8mb4",
    "allow_local_infile": True
}

PBF_FILE = "Changping.osm.pbf"
LOG_FILE = "import_log.txt"

# 昌平区坐标范围
BBOX_CHANGPING = {
    "min_lat": 40.07,
    "max_lat": 40.41,
    "min_lon": 116.00,
    "max_lon": 116.65
}

# 节点类型映射
NODE_TYPE = {
    "entrance": 0,
    "junction": 1,
    "poi": 2,
    "building": 3,
    "photo": 4,
    "regular": 5
}

# 通行方式映射
TRANSPORT_MAP = {
    "footway": 1, "path": 1, "steps": 1,
    "cycleway": 2,
    "pedestrian": 4, "living_street": 4, "residential": 4,
    "service": 4, "track": 4, "unclassified": 4,
    "tertiary": 5, "secondary": 5, "primary": 5, "trunk": 5,
    "motorway": 3,
}

WALK_SPEED = 1.2
BIKE_SPEED = 4.0
SHUTTLE_SPEED = 8.0


# ========== 工具函数 ==========
def haversine(lat1, lon1, lat2, lon2):
    R = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def in_bbox(lat, lon, bbox):
    return (bbox["min_lat"] <= lat <= bbox["max_lat"] and
            bbox["min_lon"] <= lon <= bbox["max_lon"])


def classify_node(tags):
    tag_keys = set(tags.keys())
    if tag_keys & {"amenity", "tourism", "shop", "leisure", "historic"}:
        return NODE_TYPE["poi"]
    if "entrance" in tag_keys or "barrier" in tag_keys:
        return NODE_TYPE["entrance"]
    if "building" in tag_keys:
        return NODE_TYPE["building"]
    if "highway" in tag_keys:
        return NODE_TYPE["junction"]
    return NODE_TYPE["regular"]


def determine_transport_type(highway_tag):
    return TRANSPORT_MAP.get(highway_tag, 4)


def log(msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{ts}] {msg}"
    print(line)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


# ========== OSM 处理器 ==========
class OSMImporter(osmium.SimpleHandler):
    def __init__(self, db, bbox):
        super().__init__()
        self.db = db
        self.cursor = db.cursor()
        self.bbox = bbox

        # 统计
        self.total_nodes_parsed = 0
        self.nodes_imported = 0
        self.total_ways_parsed = 0
        self.edges_imported = 0
        self.poi_count = 0
        self.entrance_count = 0

        # ★ 关键修复：两个独立的缓存
        #   node_coords: {osm_id: (lat, lng)} — 用于 Haversine 距离计算
        #   node_id_map: {osm_id: db_pk_id}  — 用于边的 FK 引用
        self.node_coords = {}
        self.node_id_map = {}

        self.start_time = datetime.now()
        self.commit_counter = 0

    def node(self, n):
        self.total_nodes_parsed += 1

        if not in_bbox(n.location.lat, n.location.lon, self.bbox):
            return

        tags = {}
        node_name = ""
        for t in n.tags:
            tags[t.k] = t.v
            if t.k == "name":
                node_name = t.v

        node_type = classify_node(tags)
        if node_type == NODE_TYPE["poi"]:
            self.poi_count += 1
        elif node_type == NODE_TYPE["entrance"]:
            self.entrance_count += 1

        is_important = 1 if node_name else 0

        try:
            sql = """
                INSERT INTO t_navigation_road_node
                (osm_id, osm_tags, name, node_type, latitude, longitude,
                 floor_number, is_important, is_accessible, is_enabled, is_deleted)
                VALUES (%s, %s, %s, %s, %s, %s, 1, %s, 1, 1, 0)
            """
            self.cursor.execute(sql, (
                n.id,
                json.dumps(tags, ensure_ascii=False) if tags else None,
                node_name if node_name else None,
                node_type,
                n.location.lat,
                n.location.lon,
                is_important
            ))

            # ★ 核心修复：用 cursor.lastrowid 获取自增主键
            db_pk_id = self.cursor.lastrowid
            self.node_coords[n.id] = (n.location.lat, n.location.lon)
            self.node_id_map[n.id] = db_pk_id

            self.nodes_imported += 1

        except Error as e:
            log(f"  ERROR inserting node osm_id={n.id}: {e}")

        if self.nodes_imported % 5000 == 0:
            self.db.commit()
            log(f"  Nodes: {self.nodes_imported} (POI:{self.poi_count} 入口:{self.entrance_count})")

    def way(self, w):
        self.total_ways_parsed += 1

        highway_tag = None
        tags = {}
        way_name = ""
        for t in w.tags:
            tags[t.k] = t.v
            if t.k == "highway":
                highway_tag = t.v
            if t.k == "name":
                way_name = t.v

        if not highway_tag:
            return

        transport_type = determine_transport_type(highway_tag)
        surface = tags.get("surface", "")
        incline_str = tags.get("incline", "")
        incline_val = None
        if incline_str:
            try:
                incline_val = float(incline_str.replace("%", ""))
            except ValueError:
                pass

        edges_this_way = 0

        # ★ 核心修复：逐对检查连续节点，两个都在缓存中才创建边
        for i in range(len(w.nodes) - 1):
            osm_from = w.nodes[i].ref
            osm_to = w.nodes[i + 1].ref

            if osm_from not in self.node_coords or osm_to not in self.node_coords:
                continue

            lat1, lon1 = self.node_coords[osm_from]
            lat2, lon2 = self.node_coords[osm_to]
            dist = round(haversine(lat1, lon1, lat2, lon2), 2)
            if dist < 0.01:
                continue

            walk_time = max(1, int(dist / WALK_SPEED))
            bike_time = max(1, int(dist / BIKE_SPEED))
            shuttle_time = max(1, int(dist / SHUTTLE_SPEED))

            # ★ 核心修复：用 node_id_map 取 db_pk_id
            try:
                sql = """
                    INSERT INTO t_navigation_road_edge
                    (osm_way_id, osm_tags, from_node_id, to_node_id, name,
                     distance, walk_time, bike_time, shuttle_time,
                     transport_type, is_bidirectional, highway_type,
                     surface, incline, is_enabled, is_deleted)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 1, 0)
                """
                self.cursor.execute(sql, (
                    w.id,
                    json.dumps(tags, ensure_ascii=False) if tags else None,
                    self.node_id_map[osm_from],   # ← 数据库主键
                    self.node_id_map[osm_to],     # ← 数据库主键
                    way_name if way_name else None,
                    dist, walk_time, bike_time, shuttle_time,
                    transport_type,
                    1,
                    highway_tag,
                    surface if surface else None,
                    incline_val
                ))
                edges_this_way += 1
            except Error as e:
                log(f"  ERROR edge {osm_from}->{osm_to}: {e}")

        self.edges_imported += edges_this_way

        self.commit_counter += 1
        if self.commit_counter % 500 == 0:
            self.db.commit()
            log(f"  Ways: {self.total_ways_parsed}, Edges: {self.edges_imported}")

    def finish(self):
        self.db.commit()
        elapsed = (datetime.now() - self.start_time).total_seconds()

        # 写入 OSM 导入日志
        try:
            sql = """
                INSERT INTO t_navigation_osm_import_log
                (file_name, file_size, region, import_type,
                 total_records, nodes_imported, edges_imported,
                 status, started_at, finished_at, processing_time_ms)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """
            file_size = os.path.getsize(PBF_FILE) if os.path.exists(PBF_FILE) else 0
            self.cursor.execute(sql, (
                PBF_FILE, file_size, "北京昌平区", "pbf",
                self.total_nodes_parsed + self.total_ways_parsed,
                self.nodes_imported, self.edges_imported,
                "completed", self.start_time, datetime.now(),
                int(elapsed * 1000)
            ))
            self.db.commit()
        except Error as e:
            log(f"  WARN: Could not write import log: {e}")

        # 释放缓存内存
        self.node_coords.clear()
        self.node_id_map.clear()

        log("")
        log("=" * 60)
        log(f"  导入完成! 耗时: {elapsed:.1f} 秒")
        log(f"  解析节点: {self.total_nodes_parsed}")
        log(f"  导入节点: {self.nodes_imported} (POI:{self.poi_count} 入口:{self.entrance_count})")
        log(f"  解析道路: {self.total_ways_parsed}")
        log(f"  导入边:   {self.edges_imported}")
        log("=" * 60)


# ========== 主程序 ==========
def main():
    log("=" * 60)
    log("JourneyCraft - 昌平区 OSM PBF 导入 v2")
    log(f"PBF: {PBF_FILE}")
    log(f"BBOX: lat[{BBOX_CHANGPING['min_lat']},{BBOX_CHANGPING['max_lat']}] lon[{BBOX_CHANGPING['min_lon']},{BBOX_CHANGPING['max_lon']}]")
    log("=" * 60)

    if not os.path.exists(PBF_FILE):
        log(f"FATAL: {PBF_FILE} 不存在!")
        sys.exit(1)

    log(f"PBF 大小: {os.path.getsize(PBF_FILE)/(1024*1024):.1f} MB")

    # 连接 MySQL
    try:
        db = mysql.connector.connect(**MYSQL_CONFIG)
        log("MySQL 连接成功")
    except Error as e:
        log(f"FATAL: MySQL 连接失败: {e}")
        sys.exit(1)

    # 清空旧数据
    log("清空旧路网数据...")
    cur = db.cursor()
    cur.execute("SET FOREIGN_KEY_CHECKS = 0")
    cur.execute("TRUNCATE t_navigation_road_edge")
    cur.execute("TRUNCATE t_navigation_road_node")
    cur.execute("SET FOREIGN_KEY_CHECKS = 1")
    db.commit()
    log("旧数据已清空")

    # 导入
    try:
        handler = OSMImporter(db, BBOX_CHANGPING)
        handler.apply_file(PBF_FILE)
        handler.finish()

        # 最终统计
        cur.execute("SELECT COUNT(*) FROM t_navigation_road_node WHERE is_deleted = 0")
        nodes = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM t_navigation_road_edge WHERE is_deleted = 0")
        edges = cur.fetchone()[0]

        log("")
        log(f"  DB 最终: {nodes} 节点, {edges} 边")

        # 节点类型分布
        cur.execute("""SELECT node_type, COUNT(*) FROM t_navigation_road_node
                       WHERE is_deleted=0 GROUP BY node_type ORDER BY node_type""")
        names = {0: "入口", 1: "路口", 2: "POI", 3: "建筑入口", 4: "拍照点", 5: "普通节点"}
        log("")
        log("  节点类型:")
        for row in cur.fetchall():
            log(f"    {names.get(row[0], str(row[0])):6s}: {row[1]}")

        # 路径类型 Top 10
        cur.execute("""SELECT highway_type, COUNT(*) FROM t_navigation_road_edge
                       WHERE is_deleted=0 GROUP BY highway_type
                       ORDER BY COUNT(*) DESC LIMIT 10""")
        log("")
        log("  路径类型 Top 10:")
        for row in cur.fetchall():
            ht = row[0] if row[0] else "unknown"
            log(f"    {ht:15s}: {row[1]}")

        cur.close()

    except Exception as e:
        log(f"FATAL: {e}")
        import traceback
        log(traceback.format_exc())
        db.rollback()
    finally:
        db.close()
        log("MySQL 连接已关闭")


if __name__ == "__main__":
    main()
