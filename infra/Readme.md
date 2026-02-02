# Overview
## การจัดการ Package ด้วย Helm

**Helm** เปรียบเสมือน "App Store" หรือ `apt-get` สำหรับ Kubernetes ช่วยให้เราติดตั้งและจัดการแอปพลิเคชันที่ซับซ้อนได้ง่ายขึ้นผ่านสิ่งที่เรียกว่า **Charts**

### ทำไมต้องใช้ Helm ในโปรเจกต์นี้?

ในขั้นตอนถัดไป เราจำเป็นต้องติดตั้ง **Spark Operator** เพื่อให้ Kubernetes รู้จักและจัดการงาน Spark ของเราได้ ซึ่งการลงผ่าน Helm จะช่วยจัดการเรื่องสิทธิ์ (RBAC) และการตั้งค่าพื้นฐานให้อัตโนมัติ

### ตรวจสอบการติดตั้ง Helm

สามารถติดตั้งได้ตาม [Official Guide](https://helm.sh/docs/intro/install/) หรือเช็คเวอร์ชันด้วยคำสั่ง:

```bash
helm version
```

---

## ⚡️ ติดตั้ง Spark Operator (ผ่าน Helm)

เพื่อให้ไฟล์ YAML ที่มี `kind: SparkApplication` ของคุณทำงานได้ เราต้องลง Operator ตัวนี้ก่อนครับ:

1. **เพิ่ม Helm Repo:**

```bash
helm repo add spark-operator https://kubeflow.github.io/spark-operator
helm repo update
```

2. **ติดตั้ง Operator 
2.1 ลงใน namespace `argo`:**
```bash
helm install my-spark-operator spark-operator/spark-operator \
    --namespace argo \
    --set webhook.enable=true
```

(การตั้งค่า `webhook.enable=true` จะช่วยให้ Kubernetes ตรวจสอบความถูกต้องของไฟล์ YAML ของเราก่อนสั่งรันครับ)

2.2 แยก namespace

```bash
helm install <release_name> spark-operator/spark-operator \
  --namespace <namespace> \
  --create-namespace \
  --set webhook.enable=true

```
> หากเลือกทำ namespace แยกให้เช็ค namespace ต่อด้วย
```bash
kubectl get ns
```
กรณีอยากลบ 
- ลบ release 
```bash
helm uninstall <release_name> --namespace <namespace>
```
- ลบ namespace
```
kubectl delete namespace <namespace>
```
## 🔍 ตรวจสอบความพร้อมของระบบ

นอกจากการเช็ค Pod ของ Argo แล้ว อย่าลืมเช็ค Pod ของ Spark Operator ด้วย:

```bash
kubectl get pods -n argo -l app.kubernetes.io/name=spark-operator
```

## 🔐 การจัดการสิทธิ์ (RBAC) สำหรับ Spark Operator และ Argo Workflow

ในโลกของ Kubernetes การมีไฟล์ YAML อย่างเดียว **ยังไม่เพียงพอ**
เพราะ Kubernetes เป็นระบบที่ **ไม่เชื่อใจอะไรโดยค่าเริ่มต้น (Zero Trust)**

เปรียบเทียบง่าย ๆ:

> เรามี “ใบสั่งงาน” (YAML)
> แต่ถ้า “คนทำงาน” ไม่มีบัตรพนักงาน (ServiceAccount)
> ก็จะเข้าโรงงานและสั่งเครื่องจักรไม่ได้

สิ่งที่ทำหน้าที่เป็น “บัตร + กฎสิทธิ์” ก็คือ **RBAC (Role-Based Access Control)**


## 🤔 ทำไมโปรเจกต์นี้ต้องมี RBAC?

ใน Pipeline นี้ มีผู้เล่นหลัก 2 ตัว:

### Argo Workflow

* ทำหน้าที่ orchestration
* ต้อง **สร้าง resource ชื่อ `SparkApplication`**
* ถ้าไม่มีสิทธิ์ → Workflow จะ fail ทันที

### Spark Driver (รันใน Pod)

* ถูกสร้างโดย Spark Operator
* ต้องมีสิทธิ์:

  * สร้าง Pod ใหม่ (Executor)
  * ดูสถานะ Pod ที่สร้าง
* ถ้าไม่มีสิทธิ์ → Spark job จะค้างหรือ fail แบบงง ๆ

---

## ภาพรวมสิทธิ์ที่ต้องมี

| ตัวไหน         | ต้องทำอะไร                              |
| -------------- | --------------------------------------- |
| Argo Workflow  | create / get / watch `SparkApplication` |
| Spark Driver   | create / get / delete `Pod`             |
| Spark Operator | watch / manage `SparkApplication`       |

---

## ServiceAccount ที่เราจะใช้

ในโปรเจกต์นี้ เราจะแยกหน้าที่ชัดเจน:

| ServiceAccount      | ใช้โดย                  |
| ------------------- | ----------------------- |
| `argo`              | Argo Workflow           |
| `spark-operator-sa` | Spark Driver / Executor |

---

## สร้าง ServiceAccount สำหรับ Spark

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

Role นี้กำหนดว่า Spark Driver **ทำอะไรได้บ้างใน namespace**

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: spark-driver-role
  namespace: argo
rules:
  - apiGroups: [""]
    resources: ["pods", "services", "configmaps"]
    verbs: ["create", "get", "list", "watch", "delete"]
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
kubectl apply -f spark-rbac.yaml
```

---

## ผูก SparkApplication ให้ใช้ ServiceAccount นี้

ในไฟล์ `SparkApplication`:

```yaml
spec:
  driver:
    serviceAccount: spark-operator-sa
```

ถ้าไม่ระบุ → จะใช้ `default` service account ซึ่ง **แทบไม่มีสิทธิ์อะไรเลย**

---

## 🔍 ตรวจสอบว่า RBAC ถูกต้องหรือไม่

### เช็ค ServiceAccount

```bash
kubectl get sa -n argo
```

### เช็ค Role / RoleBinding

```bash
kubectl get role,rolebinding -n argo
```

### เช็คเชิงลึก (simulate permission)

```bash
kubectl auth can-i create pods \
  --as=system:serviceaccount:argo:spark-operator-sa \
  -n argo
```
ลองเช็คเพิ่ม:
```bash
kubectl auth can-i create services \
  --as=system:serviceaccount:argo:spark-operator-sa \
  -n argo
```
