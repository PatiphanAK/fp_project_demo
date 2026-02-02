# สร้าง Kubernetes Cluster ด้วย Kind
## สร้าง Cluster
สร้างคลัสเตอร์ชื่อ `data-pipeline` โดยใช้ Kind:

```bash
kind create cluster --name data-pipeline
```

## สร้าง Namespace
ใน Kubernetes, **เนมสเปซ (Namespace)** เป็นกลไกสำหรับแยกกลุ่มทรัพยากรภายในคลัสเตอร์เดียวกัน:  
- ชื่อทรัพยากรต้องไม่ซ้ำกันภายในเนมสเปซเดียวกัน  
- แต่สามารถซ้ำกันได้ระหว่างเนมสเปซต่างกัน  
- การกำหนดขอบเขตด้วยเนมสเปซใช้ได้เฉพาะกับ **ออบเจกต์ระดับเนมสเปซ** เช่น `Deployment`, `Service`  
- ส่วนออบเจกต์ระดับคลัสเตอร์ เช่น `StorageClass`, `Node`, `PersistentVolume` **ไม่อยู่ภายใต้เนมสเปซ**

> อ้างอิง: [Kubernetes Documentation: Namespaces](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/)

สร้างเนมสเปซที่จำเป็น:

```bash
kubectl create namespace argo
```

ตรวจสอบว่าเนมสเปซสร้างสำเร็จ:
```bash
kubectl get ns | argo
```

ติดตั้ง Argo Workflow

```
kubectl apply -n argo -f https://github.com/argoproj/argo-workflows/releases/latest/download/install.yaml
```

ตรวจสอบว่า service Argo Workflow ขึ้นมารึยัง 

เพื่อดู Pod ทั้งหมดใน Namespace argo
```
kubectl get pods -n argo
```

## เข้าถึง Argo Workflow UI

เราสามารถ forward port จากภายใน Kubernetes cluster มายังเครื่อง host เพื่อเข้าใช้งานเว็บอินเทอร์เฟซของ Argo Workflow ได้ดังนี้:

```bash
kubectl -n argo port-forward deployment/argo-server 2746:2746
```
จากนั้นคลิกไปที่ [https://localhost:2746](https://localhost:2746)


เราจะพบว่า Argo Workflow ต้องการ Authentication การใช้ `--auth-mode server` (เข้าได้เลยไม่ต้องล็อกอิน) คือเรื่องต้องห้ามใน Production
แต่ถ้าไม่ไม่ต้องการยุ่งยากในการ configuration เราสามารถเข้าไปแก้ YAML ไฟล์ ได้ทันทีด้วย CLI

```bash
kubectl patch deployment argo-server -n argo --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/args", "value": ["server", "--auth-mode", "server"]}]'
```

หรือใน Path นี้การมี Makefile สำหรับสร้าง namespace และ argo workflow version development สำเร็จรูป
คือ setup_argo.sh

ตัวอย่าง CLI ลบ Workflow
```bash
kubectl delete workflows --all -n argo
```

หากอยากศึกษาเพิ่มเพิ่ม [Argo Workflows - The workflow engine for Kubernetes](https://argo-workflows.readthedocs.io/en/latest/)

## เมื่อ Workflow/Pipeline Failed เรา Debug ยังไง
### ดูสถานะ Workflow / Job ก่อน (ภาพรวม)

เริ่มจากดูว่า fail ที่ขั้นตอนไหน

```bash
kubectl get pods -n <namespace>
kubectl workflow -n <namespace>
```

> จะช่วยให้รู้ว่า **pod ไหน fail** และเป็น step ไหนของ pipeline

---

### Inspect Pod ที่ล้มเหลว

ดูรายละเอียดของ pod ที่มีสถานะ `Error`, `CrashLoopBackOff`, `Failed`

```bash
kubectl describe pod <pod_name>
```

สิ่งที่ควรดูเป็นพิเศษ:

* `Events` (ล่างสุด)
* `Reason` / `Exit Code`
* `ImagePullBackOff`, `OOMKilled`, `Error`
