# 开发与维护

## 构建

需要 Java 17。

```bash
mvn -f server/pom.xml verify
cd client
./gradlew build
```

Windows使用 `gradlew.bat build`。输出分别位于 `server/target/` 和 `client/build/libs/`。GitHub Actions也构建双端并保存产物。

## 统一版本

**只修改 `server/pom.xml` 的项目版本。**

- Maven注入服务端 `plugin.yml`。
- Gradle读取同一个POM版本，生成客户端 `fabric.mod.json`。
- 客户端运行及上报版本从Fabric元数据读取。
- 服务端以自身发布版本校验客户端，保留上一个维护批次的协议兼容。

发布时同步更新README下载链接与CHANGELOG，创建对应tag，上传双端JAR及配置到Release。不要把成品JAR提交到源码目录。

## 文档与配置

当前使用指南、验证记录使用固定路径，避免每次升级留下重复使用文档。配置的唯一来源是 `server/src/main/resources/config.yml`。
CHANGELOG保留历史更新记录；旧tag与Release保留作版本追溯。

## 提交身份

```bash
git config user.name "Keran Technology"
git config user.email "240964079+HifumiDaisuki01@users.noreply.github.com"
```

`.mailmap`归并历史tech、dev、FAQ邮箱，供支持mailmap的Git统计工具使用。GitHub历史贡献归属还取决于邮箱与账号关联；mailmap不改写历史提交或已发布tag。
