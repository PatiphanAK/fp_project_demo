# รู้จัก Kubernetes (K8s)
![K8s](https://download.logo.wine/logo/Kubernetes/Kubernetes-Logo.wine.png)
**Kubernetes (K8s)** คือระบบสำหรับจัดการ Container แบบอัตโนมัติ (Container Orchestration)  
ช่วยให้เราสามารถ:
- Deploy แอป
- Scale
- Restart เมื่อแอปล้ม
- จัดการ resource

โดย Kubernetes จะทำงานบนแนวคิดว่า  
> เราบอก “ต้องการอะไร” ไม่ใช่ “ทำยังไง”

## Object พื้นฐานที่ต้องรู้ก่อนลงมือทำตาม

| Object | หน้าที่ |
|------|--------|
| Pod | หน่วยเล็กสุดในการรัน Container |
| Deployment | ควบคุม lifecycle ของ Pod |
| Service | เปิดให้ Pod ติดต่อกันหรือออกภายนอก |
| Namespace | แยก logical environment |
| Node | เครื่องที่รัน Pod |
| Cluster | กลุ่มของ Node ทั้งหมด |

> Argo Workflow ทำงานบน Pod โดยตรง
ดังนั้นถ้าเข้าใจ Pod = เข้าใจ Workflow ไปแล้วครึ่งหนึ่ง

## Kubernetes กับ Data Pipeline
Kubernetes เหมาะกับ Data Pipeline เพราะ:
- แต่ละ step แยกเป็น Pod ได้
- Retry / Fail isolation
- Scale ได้ตาม workload
- เหมาะกับ ML / ETL / Batch Job

Argo Workflows จึงถูกออกแบบมาเพื่อรัน Pipeline บน Kubernetes โดยเฉพาะ
