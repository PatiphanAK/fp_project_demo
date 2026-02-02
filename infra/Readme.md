# Overview

## การจัดการ Package ด้วย Helm

**Helm** เปรียบเสมือน “App Store” หรือ `apt-get` สำหรับ Kubernetes
ช่วยให้เราติดตั้งและจัดการแอปพลิเคชันที่ซับซ้อน (เช่น Operator, Controller) ได้ง่าย ผ่านสิ่งที่เรียกว่า **Charts**

ในโปรเจกต์นี้ Helm จะถูกใช้เพื่อ:

* ติดตั้ง **Spark Operator**
* จัดการ dependency และ RBAC พื้นฐาน
* ลดความซับซ้อนของ YAML จำนวนมาก

---

## ทำไมต้องใช้ Helm ในโปรเจกต์นี้?

Pipeline นี้ใช้ **Spark บน Kubernetes**
ซึ่ง Kubernetes จะไม่รู้จัก resource ประเภท `SparkApplication` โดยค่าเริ่มต้น

ดังนั้นเราจำเป็นต้องติดตั้ง **Spark Operator** เพื่อ:

* เพิ่ม CRD (`SparkApplication`)
* สร้าง Spark Driver / Executor Pods
* จัดการ lifecycle ของ Spark job

การติดตั้งผ่าน Helm จะช่วย:

* สร้าง RBAC ให้ Operator อัตโนมัติ
* ตั้งค่า webhook และ controller ได้ครบ
* ถอนการติดตั้งได้สะอาด

---

## ตรวจสอบการติดตั้ง Helm

สามารถติดตั้ง Helm ได้จาก
👉 [https://helm.sh/docs/intro/install/](https://helm.sh/docs/intro/install/)

หรือเช็คเวอร์ชันด้วยคำสั่ง:

```bash
helm version
```

---

## ⚡️ ติดตั้ง Spark Operator (ผ่าน Helm)

เพื่อให้ไฟล์ YAML ที่มี `kind: SparkApplication` ทำงานได้
เราต้องติดตั้ง Spark Operator ก่อนเสมอ

---

### เพิ่ม Helm Repository

```bash
helm repo add spark-operator https://kubeflow.github.io/spark-operator
helm repo update
```

---

### ติดตั้ง Spark Operator

#### 2.1 ติดตั้งใน namespace เดียวกับ Argo (`argo`)

เหมาะสำหรับ demo หรือ environment ขนาดเล็ก

```bash
helm install my-spark-operator spark-operator/spark-operator \
  --namespace argo \
  --set webhook.enable=true
```

> `webhook.enable=true`
> จะช่วย validate YAML ของ `SparkApplication` ก่อนรัน
> ลด error จาก config ผิดตั้งแต่ต้น

---

#### 2.2 ติดตั้งแบบแยก namespace (แนะนำสำหรับโปรเจกต์จริง)

##### กรณีให้ Spark Operator watch ทุก namespace

```bash
helm install my-spark-operator spark-operator/spark-operator \
  --namespace spark-operator \
  --create-namespace \
  --set webhook.enable=true \
  --set watchEverywhere=true
```

##### กรณีให้ watch แค่ namespace เป้าหมาย

```bash
kubectl create ns spark-operator  # สำหรับตัวควบคุม (Controller)
kubectl create ns spark-apps      # สำหรับรัน Spark Jobs
```

```bash
helm install my-spark-operator spark-operator/spark-operator \
  --namespace spark-operator \
  --create-namespace \
  --set 'spark.jobNamespaces={spark-apps}' \
  --set webhook.enable=true
```

> จุดเช็ก: รัน 
```bash 
kubectl get pods -n spark-operator
```
> ต้องเห็นสถานะ Running

### ลบ Spark Operator (กรณี reset ระบบ)

* ลบ Helm release

```bash
helm uninstall <release_name> --namespace <namespace>
```

* ลบ namespace

```bash
kubectl delete namespace <namespace>
```

---

### ตรวจสอบว่า Spark Operator กำลัง Watch อะไรอยู่

```bash
kubectl get pod -n spark-operator -l app.kubernetes.io/name=spark-operator -o yaml | grep -A 5 "args:"
```
[Running Multiple Instances of the Spark Operator](https://www.kubeflow.org/docs/components/spark-operator/user-guide/running-multiple-instances-of-the-operator/)

หรือดูจาก args ของ container:

```bash
kubectl get pod -n <namespace> \
  -l app.kubernetes.io/name=spark-operator \
  -o jsonpath='{.items[0].spec.containers[0].args}'
```

---

## 🔍 ตรวจสอบความพร้อมของระบบ

ตรวจสอบว่า Spark Operator ทำงานอยู่จริง:

```bash
kubectl get pods -n argo -l app.kubernetes.io/name=spark-operator
```

---

## 🔐 การจัดการสิทธิ์ (RBAC) สำหรับ Spark Operator และ Argo Workflow

ใน Kubernetes การมีไฟล์ YAML อย่างเดียว **ยังไม่เพียงพอ**

เพราะ Kubernetes ใช้แนวคิด **Zero Trust**
ไม่มี resource ไหนมีสิทธิ์ทำอะไรได้โดยอัตโนมัติ

เปรียบเทียบง่าย ๆ:

> เรามี “ใบสั่งงาน” (YAML)
> แต่ถ้า “คนทำงาน” ไม่มีบัตรพนักงาน (ServiceAccount)
> ก็ไม่สามารถสั่งเครื่องจักรได้

สิ่งที่ทำหน้าที่เป็น “บัตร + กฎสิทธิ์” คือ **RBAC**

---

## 🤔 ทำไม Pipeline นี้ต้องมี RBAC?

ใน Pipeline นี้ มีผู้เล่นหลัก 2 ตัว:

### Argo Workflow

* ทำหน้าที่ orchestration
* ต้องสร้าง resource ชื่อ `SparkApplication`
* ถ้าไม่มีสิทธิ์ → Workflow fail ทันที

### Spark Driver (รันใน Pod)

* ถูกสร้างโดย Spark Operator
* ต้องมีสิทธิ์:

  * สร้าง Executor Pods
  * ตรวจสอบสถานะ Pod
* ถ้าไม่มีสิทธิ์ → Spark job จะค้างหรือ fail แบบไม่ชัดเจน

---

## ภาพรวมสิทธิ์ที่ต้องมี

| Component      | สิทธิ์ที่ต้องใช้                        |
| -------------- | --------------------------------------- |
| Argo Workflow  | create / get / watch `SparkApplication` |
| Spark Driver   | create / get / delete `Pod`             |
| Spark Operator | watch / manage `SparkApplication`       |

---

## ServiceAccount ที่ใช้ในโปรเจกต์

เพื่อแยกหน้าที่ให้ชัดเจน เราใช้ ServiceAccount ดังนี้:

| ServiceAccount      | ใช้โดย                  |
| ------------------- | ----------------------- |
| `argo`              | Argo Workflow           |
| `spark-operator-sa` | Spark Driver / Executor |

---

## สร้าง ServiceAccount สำหรับ Spark Driver

> สร้างใน namespace เดียวกับ SparkApplication (เช่น `argo`)

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spark-operator-sa
  namespace: argo
```

Apply:

```bash
kubectl apply -f spark-sa.yaml
```

---

## สร้าง Role สำหรับ Spark Driver

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: spark-driver-role
  namespace: argo
rules:
  - apiGroups: [""]
    resources:
      - pods
      - services
      - configmaps
    verbs:
      - create
      - get
      - list
      - watch
      - delete
```

---

## ผูก Role เข้ากับ ServiceAccount (RoleBinding)

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: spark-driver-binding
  namespace: argo
subjects:
  - kind: ServiceAccount
    name: spark-operator-sa
    namespace: argo
roleRef:
  kind: Role
  name: spark-driver-role
  apiGroup: rbac.authorization.k8s.io
```

Apply:

```bash
kubectl apply -f rbac.yaml
```

---

## ผูก SparkApplication ให้ใช้ ServiceAccount นี้

ในไฟล์ `SparkApplication`:

```yaml
spec:
  driver:
    serviceAccount: spark-operator-sa
```

> หากไม่ระบุ
> Spark จะใช้ `default` ServiceAccount
> ซึ่งแทบไม่มีสิทธิ์อะไรเลย → job จะ fail แน่นอน

---

## 🔍 ตรวจสอบว่า RBAC ถูกต้องหรือไม่

### ตรวจสอบ ServiceAccount

```bash
kubectl get sa -n argo
```

### ตรวจสอบ Role และ RoleBinding

```bash
kubectl get role,rolebinding -n argo
```

### ตรวจสอบเชิงลึก (simulate permission)

```bash
kubectl auth can-i create pods \
  --as=system:serviceaccount:argo:spark-operator-sa \
  -n argo
```

ลองเพิ่ม:

```bash
kubectl auth can-i create services \
  --as=system:serviceaccount:argo:spark-operator-sa \
  -n argo
```

ถ้าได้ `yes` แปลว่า RBAC ถูกต้อง

### ปัญหาที่เจอบ่อย
ชื่อใน rbac.yaml กับชื่อที่ Helm สร้างไม่ใช่้ชื่อเดียวกัน
1. เช็คชื่อก่อนเลย
```bash
kubectl get sa -n spark-operator
```
2. แก้ให้ตรงกันใน YAML แล้ว apply ใหม่

ล้าง spark-app
```bash
kubectl delete sparkapplication -n spark-apps --all
```
