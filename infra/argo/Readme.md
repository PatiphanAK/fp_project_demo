# Argo Workflows
![Argo](https://argoproj.github.io/static/6e944804f836bce176feffed44a8bf7e/93d75/workflows.png)
> Workflow engine สำหรับรัน Data Pipeline และ ML Pipeline บน Kubernetes แบบ Declarative

---

## Argo Workflows คืออะไร?

Argo Workflows คือระบบจัดการ Pipeline บน Kubernetes ที่แต่ละ Step จะถูกรันในรูปแบบของ **Pod** แยกจากกัน แทนที่จะเขียน Pipeline ด้วย Shell Script หรือ Cron Job Argo ใช้ **YAML** เพื่ออธิบาย Workflow และให้ Kubernetes จัดการการ Execute ทั้งหมด

**Use cases หลักที่เหมาะกับ Argo:**
- Data Pipeline / ETL (Extract → Transform → Load)
- Machine Learning Pipeline (Preprocess → Train → Evaluate)
- Batch Job ที่ต้องรันเป็นลำดับขั้นตอน
- งานที่ต้องการ Parallel Processing

---

## แนวคิดสำคัญ

### 1 Step = 1 Pod

แต่ละ Step ใน Workflow จะถูก Spawn เป็น Pod แยกกัน ทำให้:
- แยก Resource ได้ชัดเจนในแต่ละ Step
- Step ที่ Fail ไม่กระทบ Step อื่นโดยตรง
- Debug ง่ายขึ้น เพราะดู Log ได้ต่อ Pod

---

### ประเภทของ Workflow

#### Sequential — รันทีละขั้นตามลำดับ

```
Workflow
 ├─ Step A → Pod
 ├─ Step B → Pod
 └─ Step C → Pod
```

เหมาะกับงานที่แต่ละ Step ขึ้นต่อกัน เช่น ETL หรือ ML Training Pipeline

---

#### Parallel — รันหลาย Step พร้อมกัน

```
Workflow
 ├─ Step A ─┐
 │          ├─→ ทำงานพร้อมกัน
 └─ Step B ─┘
    Step C → Pod (แยกอิสระ)
```

เหมาะกับงานที่ไม่ขึ้นต่อกัน เช่น Data Validation หลาย Source หรือการ Process ข้อมูลหลายชุดพร้อมกัน

---

#### DAG (Directed Acyclic Graph) — กำหนด Dependency อย่างละเอียด

DAG คือหัวใจสำคัญของ Argo ที่ช่วยให้ระบุได้ว่า Step ไหนต้องรอ Step ไหน และ Step ไหนรันพร้อมกันได้ โดยไม่มี Loop (Acyclic)

```
         ┌─ Step B ─┐
Step A ──┤           ├──→ Step D
         └─ Step C ─┘
```

ใช้กับ Pipeline ที่มีโครงสร้าง Dependency ซับซ้อน เช่น Feature Engineering ที่ต้องรอหลาย Data Source

---

## Workshop: ติดตั้งและใช้งานบน Kind (Local)

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)

---

### Step 1: สร้าง Kubernetes Cluster

```bash
kind create cluster --name data-pipeline
```

---

### Step 2: สร้าง Namespace

**Namespace** คือกลไกของ Kubernetes สำหรับแยกกลุ่มทรัพยากรภายใน Cluster เดียวกัน ชื่อ Resource ต้องไม่ซ้ำกันภายใน Namespace เดียวกัน แต่สามารถซ้ำกันได้ระหว่าง Namespace ต่างกัน

> **หมายเหตุ:** Namespace ใช้ได้เฉพาะกับ Namespaced Object เช่น `Deployment`, `Service` — ส่วน Cluster-scoped Object เช่น `StorageClass`, `Node`, `PersistentVolume` ไม่อยู่ภายใต้ Namespace
> 
> อ้างอิง: [Kubernetes Docs — Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

```bash
kubectl create namespace argo
```

ตรวจสอบว่าสร้างสำเร็จ:

```bash
kubectl get ns | grep argo
```

---

### Step 3: ติดตั้ง Argo Workflows

```bash
kubectl apply -n argo -f https://github.com/argoproj/argo-workflows/releases/latest/download/install.yaml
```

รอให้ Pod พร้อมใช้งาน:

```bash
kubectl get pods -n argo --watch
```

---

### Step 4: เข้าถึง Argo UI
Forward Port จาก Cluster มายังเครื่อง Host:
>> Forward Port คืออะไร
Port Forwarding คือเทคนิคใน Computer Network ที่ใช้ map / tunnel traffic
จากพอร์ตหนึ่ง ไปยังอีกพอร์ตหนึ่งของปลายทาง
```text
Client (Browser)
  |
  |  TCP :2746
  v
Host Machine (kubectl)
  |
  |  Port Forwarding (via Kubernetes API Server)
  v
Target Service (argo-server Pod)
```

```bash
kubectl -n argo port-forward deployment/argo-server 2746:2746
```

จากนั้นเปิด [https://localhost:2746](https://localhost:2746)

> **⚠️ สำหรับ Local Dev เท่านั้น:** คำสั่งด้านล่างจะ Disable Authentication (`--auth-mode server`) ซึ่ง **ห้ามใช้ใน Production** เด็ดขาด

```bash
kubectl patch deployment argo-server -n argo \
  --type='json' \
  -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/args", "value": ["server", "--auth-mode", "server"]}]'
```

> หรือใช้ `setup_argo.sh` ที่อยู่ใน Repo นี้ ซึ่งรวม Namespace Creation และการ Setup Argo สำหรับ Development ไว้ให้แล้ว

---

### คำสั่งที่ใช้บ่อย

| คำสั่ง | ความหมาย |
|--------|----------|
| `kubectl get pods -n argo` | ดู Pod ทั้งหมดใน Namespace argo |
| `kubectl delete workflows --all -n argo` | ลบ Workflow ทั้งหมด |
| `kubectl get workflows -n argo` | ดูรายการ Workflow |

---

## Debug เมื่อ Workflow ล้มเหลว

### 1. ดูภาพรวมก่อนว่า Fail ที่ขั้นตอนไหน

```bash
kubectl get pods -n <namespace>
kubectl get workflows -n <namespace>
```

จะช่วยระบุได้ว่า Pod ไหน Fail และเป็น Step ที่เท่าไรของ Pipeline

---

### 2. Inspect Pod ที่มีปัญหา

Pod ที่มักต้องสนใจคือ Pod ที่มีสถานะ `Error`, `CrashLoopBackOff`, หรือ `Failed`

```bash
kubectl describe pod <pod_name> -n <namespace>
```

ให้ดูที่ส่วน `Events` (ด้านล่างสุด) เป็นหลัก พร้อมตรวจสอบ `Reason` และ `Exit Code`

| สถานะ | ความหมาย |
|-------|----------|
| `ImagePullBackOff` | Pull Image ไม่ได้ — ตรวจสอบ Image Name / Registry Credentials |
| `OOMKilled` | ใช้ Memory เกิน Limit — เพิ่ม Memory Request/Limit |
| `Error` / Exit Code ≠ 0 | Process ใน Container Exit ด้วย Error — ดู Log ประกอบ |

---

### 3. ดู Log โดยตรง

```bash
kubectl logs <pod_name> -n <namespace>

# กรณี Pod มีหลาย Container
kubectl logs <pod_name> -n <namespace> -c <container_name>
```

---

## อ่านเพิ่มเติม

- [Argo Workflows Official Docs](https://argo-workflows.readthedocs.io/en/latest/)
- [Kubernetes Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)
- [Argo Workflows GitHub](https://github.com/argoproj/argo-workflows)
