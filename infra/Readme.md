# YAML คืออะไร

YAML ย่อมาจาก YAML Ain’t Markup Language
เป็นภาษาสำหรับเขียนไฟล์ Configuration ที่ออกแบบมาให้ คนอ่านง่าย เขียนง่าย ไม่รกสายตา

YAML ไม่ใช่ภาษาโปรแกรม
แต่เป็นภาษาไว้ อธิบายโครงสร้างข้อมูล (data structure)

ตัวอย่าง
```yaml
name: spark-job
version: 1.0
enabled: true
```

---

# ตัวอย่าง YAML: Argo Workflow – Hello World

ไฟล์: `hello-workflow.yaml`

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Workflow
metadata:
  generateName: hello-world-
  namespace: argo

spec:
  entrypoint: hello
  templates:
    - name: hello
      container:
        image: alpine:3.18
        command: [echo]
        args: ["Hello Argo Workflows!"]
```

---

## รัน Workflow

```bash
kubectl apply -f hello-workflow.yaml
```

ดูสถานะ:

```bash
kubectl get workflows -n argo
```

ดู log:

```bash
kubectl logs -n argo @latest
```

หรือกดดูผ่าน Argo UI ได้เลย

---

# อธิบาย YAML ทีละส่วน

## 1. apiVersion / kind

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Workflow
```

บอก Kubernetes ว่า:

* resource นี้เป็นของ **Argo**
* ประเภทคือ **Workflow**

> ถ้า Spark ต้องใช้ `kind: SparkApplication`
> ถ้า Pod ปกติคือ `kind: Pod`

---

## 2. metadata

```yaml
metadata:
  generateName: hello-world-
  namespace: argo
```

* `generateName`
  → ให้ Kubernetes สร้างชื่ออัตโนมัติ เช่น
  `hello-world-abcde`

* `namespace`
  → Workflow นี้จะอยู่ใน namespace `argo`

---

## 3. spec (หัวใจของ Workflow)

```yaml
spec:
  entrypoint: hello
```

* `entrypoint` คือ **จุดเริ่มต้นของ Workflow**
* ต้องตรงกับชื่อ template ด้านล่าง

> เปรียบเหมือน `main()` ในโปรแกรม

---

## 4. templates

```yaml
templates:
  - name: hello
```

* `templates` คือชุดของ “พิมพ์เขียว”
* แต่ละ template = **1 step / 1 pod**

---

## 5. container (Step-as-a-Pod)

```yaml
container:
  image: alpine:3.18
  command: [echo]
  args: ["Hello Argo Workflows!"]
```

แปลตรงตัว:

* สร้าง **Pod**
* ใช้ image `alpine`
* รันคำสั่ง:

```bash
echo "Hello Argo Workflows!"
```

> เมื่อ Pod รันเสร็จ → step สำเร็จ → workflow จบ

---

# มุมมองเชิง Architecture
```text
Argo Workflow
   |
   v
Template: hello
   |
   v
Pod (alpine)
   |
   v
echo "Hello Argo Workflows!"
```

-----
คำแนะนำ `pipeline1.yaml` มีการใช้
```yaml
            hadoopConf:
              "fs.s3a.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem"
              "fs.s3a.endpoint": "s3.ap-southeast-1.amazonaws.com"
              "fs.s3a.path.style.access": "true"
              "fs.s3a.aws.credentials.provider": "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider"
              "fs.s3a.access.key": "Not Secure"
              "fs.s3a.secret.key": "Note Secure"
```
แต่ว่าใน `pipeline2.yaml` จะ secure และตรง security practice กว่าโดยการใช้ค่า Variable จาก Environment
```yaml

            hadoopConf:
              "fs.s3a.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem"
              "fs.s3a.endpoint": "s3.ap-southeast-1.amazonaws.com"
              "fs.s3a.path.style.access": "false"
              "fs.s3a.aws.credentials.provider": "com.amazonaws.auth.EnvironmentVariableCredentialsProvider"
```
ทำได้โดย
```bash kubectl create secret generic aws-creds -n spark-apps \
  --from-literal=aws-access-key=ใส่_Access_KeyIDตรงนี้ \
  --from-literal=aws-secret-key=ใส่_Secret_AccessKeyยาวๆตรงนี้
``` 
ถ้า 
```bash
secret/aws-creds created
```
คือ Ok
