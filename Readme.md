# Functional Programming & Distributed Data Processing Demo

> **Course:** Functional Programming (Academic Year 2025)  
> **Institution:** School of Information Technology, King Mongkut's Institute of Technology Ladkrabang (IT KMITL)  
> **Status:** 🟢 Active Demo Project

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kubernetes](https://img.shields.io/badge/K8s-Ready-326CE5.svg)](https://kubernetes.io/)
[![Apache Spark](https://img.shields.io/badge/Spark-4.x-E25A1C.svg)](https://spark.apache.org/)

## 📖 Overview

This repository serves as a comprehensive demo project for students in the **Functional Programming** course. It bridges the gap between theoretical Functional Programming (FP) concepts and real-world **Distributed Data Processing** systems.

โครงการนี้ถูกออกแบบมาเพื่อเชื่อมโยงแนวคิดเชิงทฤษฎีของ **Functional Programming (FP)** เข้ากับระบบ **Distributed Data Processing** ที่ใช้งานจริงในอุตสาหกรรม

## 🎯 Learning Objectives

By completing this project, students will be able to:

1.  **Model Data Pipelines as Functional Transformations**  
    ออกแบบท่อส่งข้อมูลโดยมองว่าเป็นการแปลงค่า (Transformations) ทางฟังก์ชัน
2.  **Understand Immutability in Distributed Systems**  
    เข้าใจว่าความไม่เปลี่ยนแปลงของข้อมูล (Immutability) ช่วยลดความซับซ้อนในการ Reasoning ระบบกระจายอย่างไร
3.  **Implement Declarative ETL Workflows**  
    สร้างกระบวนการ ETL แบบ Batch โดยใช้ระบบ Workflow แบบ Declarative
4.  **Orchestrate Jobs on Kubernetes**  
    Deploy และจัดการ Distributed Jobs บน Kubernetes Cluster
5.  **Apply Algebraic Reasoning to Real Systems**  
    เชื่อมโยงเหตุผลทางพีชคณิต (Algebraic Reasoning) เข้ากับระบบ Distributed จริง

## 🛠 Technology Stack | เครื่องมือและเทคโนโลยี

| Category | Tools | Description |
| :--- | :--- | :--- |
| **Orchestration** | Kubernetes, K3s, Kind | Container Orchestration (Production vs. Local) |
| **Workflow** | Argo Workflows | Declarative Job Scheduling & DAG Management |
| **Compute** | Apache Spark, Hadoop | Distributed Data Processing Engine |
| **Storage** | AWS S3 (or MinIO) | Object Storage for Data Lake |
| **Container** | Docker, Containerd | Application Packaging & Isolation |

### Infrastructure Notes | หมายเหตุโครงสร้างพื้นฐาน
*   **Production/Cluster:** ใช้ **Kubernetes** หรือ **K3s** สำหรับ Lightweight cluster
*   **Local Development:** ใช้ **Kind (Kubernetes in Docker)** เพื่อความรวดเร็ว ไม่ต้องพึ่งพา VM ของ Cloud Provider

## 🏗 Architecture & Concepts | สถาปัตยกรรมและแนวคิด

The system architecture is designed around the **Dataflow Graph** paradigm.
สถาปัตยกรรมระบบถูกออกแบบโดยยึดหลัก **Dataflow Graph**

### Core Methodology | วิธีการหลัก
*   **DAG Execution Model:** งานถูกกำหนดเป็น Directed Acyclic Graph ซึ่งแต่ละขั้นตอนขึ้นอยู่กับผลลัพธ์ของขั้นก่อนหน้า
*   **Pure Asynchronous I/O:** การรับส่งข้อมูลเป็นแบบ Non-blocking และไม่มี Side Effect
*   **Parallel & Concurrent:** แต่ละ Node ใน DAG เปรียบเสมือน Function ที่บริสุทธิ์ (Pure Function) สามารถประมวลผลแบบขนานได้
*   **Declarative Pipelines:** กำหนด "สิ่งที่ต้องการ" (What) แทน "วิธีการทำ" (How) ใน Workflow

```mermaid
graph TD

    subgraph Routes
        R1[Route 10km]
        R2[Route 25km]
        R3[Route 1_5km]
    end

    R1 --> A1
    R2 --> A1
    R3 --> A1

    A1[Bronze Job]
    A2[Silver Job]
    A3[Gold Job]

    subgraph Bronze Execution
        B1[Driver]
        B2[Executor 1]
        B3[Executor 2]
    end

    subgraph Silver Execution
        C1[Driver]
        C2[Executor 1]
        C3[Executor 2]
    end

    subgraph Gold Execution
        D1[Driver]
        D2[Executor 1]
        D3[Executor 2]
    end

    A1 --> B1
    B1 --> B2
    B1 --> B3
    B2 --> A2
    B3 --> A2

    A2 --> C1
    C1 --> C2
    C1 --> C3
    C2 --> A3
    C3 --> A3

    A3 --> D1
    D1 --> D2
    D1 --> D3
```

## 📚 Resources & References | แหล่งเรียนรู้เพิ่มเติม
*   [Functional Programming Principles in Scala](https://www.coursera.org/learn/progfun1)
*   [Apache Spark Programming Guide](https://spark.apache.org/docs/latest/)
*   [Argo Workflows Documentation](https://argoproj.github.io/argo-workflows/)
*   IT KMITL Functional Programming Course Slides (Internal)

## 🤝 Contributing | การมีส่วนร่วม
โครงการนี้เปิดรับ Contribution จากนักศึกษาในรายวิชา หากพบข้อผิดพลาดหรือมีข้อเสนอแนะ กรุณาสร้าง Issue หรือ Pull Request

## 📄 License
This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.
**Created for IT KMITL | Academic Year 2025**
