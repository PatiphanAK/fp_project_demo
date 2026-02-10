# 🐳 Docker
![Docker](https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Docker_%28container_engine%29_logo.svg/960px-Docker_%28container_engine%29_logo.svg.png)

Docker คือ **Platform สำหรับการพัฒนาและรันแอปพลิเคชันในรูปแบบ Container**  
ซึ่งช่วยให้แอปสามารถทำงานได้เหมือนกันในทุกสภาพแวดล้อม  
ไม่ว่าจะเป็นเครื่องนักพัฒนา (Local), Server หรือ Cloud

Docker แก้ปัญหาแบบคลาสสิกอย่าง  
> “บนเครื่องผมรันได้นะครับ”

# 📦 Container

**Container** คือสภาพแวดล้อมที่แยกออกจากระบบหลัก (Isolated Environment)  
ซึ่งรวมทุกอย่างที่แอปต้องใช้ เช่น
- Source code
- Runtime
- Library และ Dependency
- Configuration

Container ใช้ **OS Kernel ร่วมกับ Host**  
ทำให้เบากว่า Virtual Machine และเริ่มทำงานได้รวดเร็ว

## ⚙️ containerd
![Conatinerd](https://raw.githubusercontent.com/cncf/artwork/master/projects/containerd/horizontal/color/containerd-horizontal-color.png)
**containerd** คือ Container Runtime ระดับล่าง (Low-level Runtime)  
ทำหน้าที่จัดการวงจรชีวิตของ Container เช่น
- Pull Image
- Create / Start / Stop Container
- จัดการ Storage และ Network
เราสามารถอ่านเพิ่มเติมได้ที่ [Containerd](https://containerd.io/)

Docker ใช้ `containerd` เป็น backend หลัก  
และใน production (เช่น Kubernetes) ก็มักใช้ `containerd` โดยตรง
```text
Docker CLI
   ↓
Docker Engine
   ↓
containerd
   ↓
runc → Linux Kernel
```

## Dockerfile
Dockerfile คือไฟล์ที่ใช้กำหนดขั้นตอนการสร้าง Docker Image
เขียนในรูปแบบ Instruction เช่น
```
FROM python:3.11
WORKDIR /app
COPY . .
RUN pip install -r requirements.txt
CMD ["python", "app.py"]
```
เราก็จะใช้ 
```bash
docker build Dockerfile
``` 
ขึ้นมาเพื่อสร้าง images

## Docker Image
Docker Image คือ Template สำหรับสร้าง Container
มีลักษณะเป็น Read-only และใช้ระบบ Layer ซึ่งเกิดขึื้นเมื่อเราทำการสร้าง Template จากการ build Dockerfile
```text
Dockerfile → Docker Image → Docker Container
```
เราสามารถเช็คได้จาก 
```bash
docker images
```

## Docker Volume
Docker Volume ใช้สำหรับเก็บข้อมูลถาวร (Persistent Data)
เพื่อไม่ให้ข้อมูลหายเมื่อ Container ถูกลบ

## Docker Hub

Docker Hub คือ Registry กลางสำหรับเก็บและแชร์ Docker Image
คล้าย GitHub แต่สำหรับ Container Image

สามารถ:

Pull Image สำเร็จรูป (nginx, mysql, node)
Push Image ของตัวเอง
ใช้ร่วมกับ CI/CD Pipeline

ตัวอย่าง:
```bash
docker pull nginx
docker push username/my-image
```
