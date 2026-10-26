#!/usr/bin/env python3
"""审批流引擎端到端验证脚本（HR_LEAVE 流程）。
流程：DEPT_LEADER(部门负责人) -> HR_AUDIT(人事经理, 条件 #leaveDays>3 才进入)
本脚本以 admin(超级管理员) 登录，发起请假 5 天的流程，自动完成所有待办直至流程结束。
"""
import json
import urllib.request

BASE = "http://localhost:8090"

def http(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())

def login(username, password):
    st, resp = http("POST", "/api/auth/login", body={"username": username, "password": password})
    assert st == 200 and resp.get("code") == 200, f"login failed: {resp}"
    return resp["data"]["token"]

def main():
    token = login("admin", "123456")
    print("== 1) 流程定义列表 ==")
    st, resp = http("GET", "/api/workflow/process/list?pageNum=1&pageSize=10", token)
    print("  code=%s total=%s names=%s" % (resp.get("code"), resp["data"]["total"],
          [p["processName"] for p in resp["data"]["rows"]]))

    print("== 2) HR_LEAVE 流程定义详情 ==")
    st, resp = http("GET", "/api/workflow/process/3", token)
    proc = resp["data"]["process"]
    print("  code=%s name=%s nodes=%s" % (resp.get("code"), proc["processName"],
          [(n["nodeName"], n["approverType"], n["approveMode"], n["conditionExpr"]) for n in resp["data"]["nodes"]]))

    print("== 3) 发起请假流程(leaveDays=5) ==")
    st, resp = http("POST", "/api/workflow/instance/start", token,
                    {"processKey": "HR_LEAVE", "title": "张三请假5天", "businessId": 1001,
                     "businessNo": "LV20260815001", "formData": json.dumps({"leaveDays": 5})})
    inst_id = resp["data"]
    print("  code=%s instanceId=%s" % (resp.get("code"), inst_id))

    print("== 4) 循环审批直至流程结束 ==")
    for i in range(1, 12):
        st, detail = http("GET", "/api/workflow/instance/%d" % inst_id, token)
        inst = detail["data"]["instance"]
        pending = [t for t in detail["data"]["tasks"] if t["status"] == 0]
        print("  -- 第%d轮: 实例状态=%s 当前节点=%s 待办数=%d"
              % (i, inst["status"], inst["currentNodeName"], len(pending)))
        if inst["status"] != 0:
            break
        if not pending:
            print("  !! 流程审批中但无待办，可能停滞")
            break
        for t in pending:
            st, resp = http("POST", "/api/workflow/task/complete", token,
                            {"taskId": t["id"], "agree": True, "comment": "同意"})
            print("     完成任务 node=%s approver=%s -> code=%s"
                  % (t["nodeName"], t["approverName"], resp.get("code")))

    print("== 5) 最终实例详情 ==")
    st, detail = http("GET", "/api/workflow/instance/%d" % inst_id, token)
    inst = detail["data"]["instance"]
    print("  实例状态=%s (0审批中/1已通过/2已驳回/3已撤销)" % inst["status"])
    print("  任务轨迹:")
    for t in detail["data"]["tasks"]:
        print("    [%s] node=%s approver=%s status=%s comment=%s"
              % (t["id"], t["nodeName"], t["approverName"], t["status"], t.get("approveComment")))
    print("  流水记录:")
    for r in detail["data"]["records"]:
        print("    %s | %s | %s | %s" % (r["operation"], r["nodeName"], r["operatorName"], r.get("approveComment")))

    ok = inst["status"] == 1
    print("\nRESULT:", "PASS ✅ 流程正常审批通过" if ok else "FAIL ❌ 流程未到达已通过状态")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
