# 文档中心

本文档索引汇总个人效能系统的业务、使用、架构、数据与各项目搭建资料。仓库首页见[根 README](../README.md)；各应用的简要说明仍保留在对应项目目录的 README 中。

## 业务与需求

| 文档 | 内容 |
|------|------|
| [业务闭环与代码阅读路径](business/business-flow-and-code-reading.md) | 介绍日 / 周 / 月业务流程、各语言实现入口和关键数据流。 |
| [中文音视频口播稿](business/business-flow-video-script.zh-CN.md) | 配套业务追踪文档的录制脚本，含分段时间码与画面提示。 |
| [需求规格说明书（2026.11.20）](requirements/requirements-2026-11-20.md) | 用户场景、功能需求、关键实体和成功标准。 |

## 使用与界面规范

| 文档 | 内容 |
|------|------|
| [简体中文使用指南](guides/user-guide.zh-CN.md) | 登录、双端切换以及日 / 周 / 月使用节奏。 |
| [English User Guide](guides/user-guide.en.md) | English guide for sign-in, PC/mobile views, and daily/weekly/monthly workflows. |
| [繁體中文使用指南](guides/user-guide.zh-TW.md) | 登入、雙端切換及日 / 週 / 月使用節奏。 |
| [日本語ユーザーガイド](guides/user-guide.ja.md) | ログイン、PC / モバイル切り替え、日次・週次・月次の利用方法。 |
| [PC / Mobile 双端实现规范](guides/dual-view-guidelines.md) | 服务端页面版的视图切换、模板组织和移动端验收要求。 |

## 架构与数据

| 文档 | 内容 |
|------|------|
| [系统架构与组件关系](architecture/architecture-diagram.md) | 架构分层、技术栈、数据存储与关键组件关系。 |
| [关键技术概览](architecture/key-technologies.md) | 项目选型与技术栈摘要。 |
| [数据模型设计](data/schema-design.md) | 业务实体、表结构映射与数据关系。 |
| [REST API 契约](data/api-contract.md) | 认证、端点、请求 / 响应字段、错误语义与权限边界。 |
| [共享数据库建表脚本](../db/schema.sql) | 当前版本数据库初始化 SQL。 |

## 各项目搭建与部署

项目 README 保留在源码目录；详细搭建、部署和工程讲解统一存放于 `docs/projects/`。

| 语言 | 项目 | 搭建指南 | 构建过程讲解 | 部署指南 |
|------|------|----------|--------------|----------|
| Go | Gin 页面单体版 | [搭建](projects/go/pms-gin/setup.md) | [构建讲解](projects/go/pms-gin/build-process-explained.md) | [部署](projects/go/pms-gin/deployment.md) |
| Go | Gin REST API | [搭建](projects/go/pms-gin-api/setup.md) | [构建讲解](projects/go/pms-gin-api/build-process-explained.md) | [部署](projects/go/pms-gin-api/deployment.md) |
| Java | Spring Boot 页面单体版 | [搭建](projects/java/pms-springboot/setup.md) | [构建讲解](projects/java/pms-springboot/build-process-explained.md) | [部署](projects/java/pms-springboot/deployment.md) |
| Java | Spring Boot REST API | [搭建](projects/java/pms-springboot-api/setup.md) | [构建讲解](projects/java/pms-springboot-api/build-process-explained.md) | [部署](projects/java/pms-springboot-api/deployment.md) |
| Python | Django 页面版 | [搭建](projects/python/pms-django/setup.md) | [构建讲解](projects/python/pms-django/build-process-explained.md) | [部署](projects/python/pms-django/deployment.md) · [不停机发布](projects/python/pms-django/hot-deployment.md) |
| Python | FastAPI + Vue 同源版 | [搭建](projects/python/pms-fastapi/setup.md) | [构建讲解](projects/python/pms-fastapi/build-process-explained.md) | [部署](projects/python/pms-fastapi/deployment.md) |
| Python | Flask 页面单体版 | [搭建](projects/python/pms-flask/setup.md) | [构建讲解](projects/python/pms-flask/build-process-explained.md) | [部署](projects/python/pms-flask/deployment.md) |
| Python | Flask REST API | [搭建](projects/python/pms-flask-api/setup.md) | [构建讲解](projects/python/pms-flask-api/build-process-explained.md) | [部署](projects/python/pms-flask-api/deployment.md) |
| Vue | 共享 Vue 前端 | [搭建](projects/vue/pms-vue/setup.md) | [构建讲解](projects/vue/pms-vue/build-process-explained.md) | [部署](projects/vue/pms-vue/deployment.md) |
| React | 共享 React 前端 | [搭建](projects/react/pms-react/setup.md) | [构建讲解](projects/react/pms-react/build-process-explained.md) | [部署](projects/react/pms-react/deployment.md) |

各语言与运行模式的 Docker、Kubernetes、Nginx Ingress、探针和发布方案统一见[多语言 Docker + Kubernetes 部署指南](guides/container-deployment.md)。

## 文档位置约定

- 项目专属详细文档放在 `docs/projects/<语言>/<项目>/`，以 `setup.md`、`deployment.md` 等名称区分用途。
- 每个项目均提供构建过程讲解和部署指南；Java 页面单体版的构建讲解同时涵盖架构与实现脉络。
- 可直接运行的源码与项目 README 仍保留在 `versions/`，避免把开发入口和代码分离。
- 数据库 SQL 保留在 `db/`；其设计说明和 API 契约归档在本目录的 `data/`。
