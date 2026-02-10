# Helm & Spark Operator

> ติดตั้งและจัดการ Spark Operator บน Kubernetes ผ่าน Helm

---

## Helm คืออะไร?
![Helm](https://helm.sh/assets/images/Logo-Tweak-Light-d053f1306a8b7f1c8c0a3856cba76397.png)
**Helm** คือ Package Manager ของ Kubernetes — เปรียบเหมือน `apt-get` หรือ `brew` แต่สำหรับ Kubernetes
แทนที่จะต้องเขียน YAML จำนวนมากด้วยตัวเอง Helm จัดการมาให้ผ่านสิ่งที่เรียกว่า **Chart** (แพ็กเกจสำเร็จรูป) ที่รวม YAML ทั้งหมดที่ต้องการไว้แล้ว

---

## ทำไมโปรเจกต์นี้ถึงต้องใช้ Helm?

Pipeline นี้ใช้ **Spark บน Kubernetes** ซึ่ง Kubernetes จะไม่รู้จัก Resource ประเภท `SparkApplication` โดย Default

เราจึงต้องติดตั้ง **Spark Operator** เพื่อ:
- เพิ่ม CRD `SparkApplication` ให้ Kubernetes รู้จัก
- สร้าง Spark Driver / Executor Pods อัตโนมัติ
- จัดการ Lifecycle ของ Spark Job

การติดตั้งผ่าน Helm ช่วยให้:
- สร้าง RBAC ให้ Operator อัตโนมัติ
- ตั้งค่า Webhook และ Controller ได้ครบ
- ถอนการติดตั้งได้สะอาดด้วยคำสั่งเดียว

---

## ตรวจสอบและติดตั้ง Helm

```bash
helm version
```

หากยังไม่มี Helm: [helm.sh/docs/intro/install](https://helm.sh/docs/intro/install/)

---

## ติดตั้ง Spark Operator

### Step 1: เพิ่ม Helm Repository

```bash
helm repo add spark-operator https://kubeflow.github.io/spark-operator
helm repo update
```

---

### Step 2: เลือก Mode การติดตั้ง

#### Option A — ติดตั้งใน Namespace เดียวกับ Argo (สำหรับ Local Dev)

เหมาะกับ Demo หรือ Environment ขนาดเล็ก

```bash
helm install my-spark-operator spark-operator/spark-operator \
  --namespace argo \
  --set webhook.enable=true
```

---

#### Option B — แยก Namespace (แนะนำสำหรับโปรเจกต์จริง)

แยก Controller ออกจาก Workload ให้ชัดเจน

**กรณีให้ Spark Operator Watch ทุก Namespace:**

```bash
helm install my-spark-operator spark-operator/spark-operator \
  --namespace spark-operator \
  --create-namespace \
  --set webhook.enable=true \
  --set watchEverywhere=true
```

**กรณีให้ Watch เฉพาะ Namespace เป้าหมาย (Recommended):**

```bash
# สร้าง Namespace แยกให้ชัดเจน
kubectl create ns spark-operator   # สำหรับตัว Controller
kubectl create ns spark-apps       # สำหรับรัน Spark Jobs

# ติดตั้ง Operator ให้ Watch เฉพาะ spark-apps
helm install my-spark-operator spark-operator/spark-operator \
  --namespace spark-operator \
  --create-namespace \
  --set 'spark.jobNamespaces={spark-apps}' \
  --set webhook.enable=true
```

> **webhook.enable=true** คือด่านตรวจ YAML ของ `SparkApplication` ก่อนที่ Kubernetes จะ Apply — ช่วยลด Error จาก Config ผิดตั้งแต่ต้น

---

### Step 3: ตรวจสอบว่า Operator พร้อม

```bash
kubectl get pods -n spark-operator
# หรือ
kubectl get pods -n argo -l app.kubernetes.io/name=spark-operator
```

ต้องเห็นสถานะ `Running`

---

## ตรวจสอบว่า Operator Watch Namespace ไหนอยู่

```bash
# ดูผ่าน args ของ Container
kubectl get pod -n spark-operator \
  -l app.kubernetes.io/name=spark-operator \
  -o jsonpath='{.items[0].spec.containers[0].args}'
```

อ้างอิง: [Running Multiple Instances of the Spark Operator](https://www.kubeflow.org/docs/components/spark-operator/user-guide/running-multiple-instances-of-the-operator/)

---

## คำสั่งที่ใช้บ่อย

| คำสั่ง | ความหมาย |
|--------|----------|
| `helm list -A` | ดู Helm Release ทั้งหมดใน Cluster |
| `helm status my-spark-operator -n spark-operator` | ดูสถานะของ Release |
| `helm upgrade my-spark-operator spark-operator/spark-operator -n spark-operator` | อัปเกรด Release |

---

## ลบ Spark Operator (กรณี Reset ระบบ)

```bash
# ลบ Helm Release
helm uninstall my-spark-operator --namespace spark-operator

# ลบ Namespace
kubectl delete namespace spark-operator
kubectl delete namespace spark-apps   # ถ้ามี
```

---

## Troubleshooting

### Operator ไม่ขึ้นมา (Pod Pending หรือ Error)

```bash
kubectl describe pod -n spark-operator -l app.kubernetes.io/name=spark-operator
```

ดูที่ `Events` ด้านล่าง — มักเกิดจาก Resource ไม่พอหรือ Image Pull ไม่ได้

---

### SparkApplication ถูก Apply แต่ไม่มีอะไรเกิดขึ้น

ตรวจสอบว่า Operator Watch Namespace ถูกต้อง:

```bash
kubectl get pod -n spark-operator \
  -l app.kubernetes.io/name=spark-operator \
  -o jsonpath='{.items[0].spec.containers[0].args}'
```

หาก Namespace ของ `SparkApplication` ไม่อยู่ใน Watch List ให้ Reinstall พร้อมแก้ `spark.jobNamespaces`

---

### ลบ SparkApplication ทั้งหมด (สำหรับ Reset)

```bash
kubectl delete sparkapplication --all -n spark-apps
```

---

## อ่านเพิ่มเติม

- [Spark Operator Official Docs](https://www.kubeflow.org/docs/components/spark-operator/)
- [Helm Docs](https://helm.sh/docs/)
