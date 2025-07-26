import requests
import re
import os
import argparse
import json
import time
from packaging import version
"""
此脚本暂不可用，无法获取准确的fabric-
"""
# 配置常量
REQUEST_TIMEOUT = 10
MAX_RETRIES = 3
BASE_API_URL = "https://meta.fabricmc.net/v2"

def get_fabric_api_version(mc_version):
    """从 Modrinth API 获取指定 Minecraft 版本的 Fabric API 版本"""
    try:
        # Modrinth API 端点，fabric-api 是项目 slug
        url = "https://api.modrinth.com/v2/project/fabric-api/version"
        params = {
            "game_versions": [mc_version],
            "loaders": ["fabric"]
        }
        
        response = requests.get(url, params=params, timeout=REQUEST_TIMEOUT)
        response.raise_for_status()
        versions = response.json()
        
        if versions:
            # 返回最新版本的版本号
            return versions[0]["version_number"]
        return None
        
    except Exception as e:
        print(f"从 Modrinth 获取 Fabric API 版本失败: {str(e)}")
        return None

def get_json_with_retry(endpoint):
    """带重试机制的JSON请求"""
    url = f"{BASE_API_URL}/{endpoint}"
    for attempt in range(MAX_RETRIES):
        try:
            response = requests.get(url, timeout=REQUEST_TIMEOUT)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"请求失败 (尝试 {attempt + 1}/{MAX_RETRIES}): {str(e)}")
            if attempt < MAX_RETRIES - 1:
                time.sleep(1)
                continue
            raise

def get_stable_versions():
    """获取所有稳定版本的Minecraft及其对应的Fabric组件版本"""
    try:
        # 获取所有版本信息
        all_data = get_json_with_retry("versions")
        
        # 获取稳定版游戏版本
        stable_versions = [
            v["version"] for v in all_data["game"] 
            if v["stable"]
        ]
        
        # 获取每个版本对应的组件
        versions_data = {}
        for mc_ver in sorted(stable_versions, key=version.parse, reverse=True):
            try:
                # 获取Loader版本
                loader_ver = next(
                    (l["version"] for l in all_data["loader"] 
                     if l["stable"]), None
                )
                
                # 获取Yarn映射 - 优先稳定版，否则取最新版
                yarn_mappings = [y for y in all_data["mappings"] if y["gameVersion"] == mc_ver]
                if yarn_mappings:
                    # 优先选择稳定版，如果没有则选择build号最大的
                    stable_yarn = next((y["version"] for y in yarn_mappings if y["stable"]), None)
                    yarn_ver = stable_yarn or max(yarn_mappings, key=lambda x: x["build"])["version"]
                else:
                    yarn_ver = None
                
                # 从 Modrinth API 获取 Fabric API 版本
                fabric_api_ver = get_fabric_api_version(mc_ver)
                
                if all([loader_ver, yarn_ver]):
                    versions_data[mc_ver] = {
                        "loader": loader_ver,
                        "yarn": yarn_ver,
                        "fabric_api": fabric_api_ver,
                        "major_version": ".".join(mc_ver.split(".")[:2])
                    }
            except Exception as e:
                print(f"处理 {mc_ver} 版本时出错: {str(e)}")
                continue
        
        return versions_data
    
    except Exception as e:
        print(f"获取版本信息失败: {str(e)}")
        return {}

def generate_properties_file(mc_ver, data, template_path, output_dir):
    """生成版本配置文件"""
    with open(template_path, 'r') as f:
        content = f.read()
    
    replacements = {
        r'minecraft_version=.*': f'minecraft_version={mc_ver}',
        r'yarn_mappings=.*': f'yarn_mappings={data["yarn"]}',
        r'loader_version=.*': f'loader_version={data["loader"]}'
    }
    
    # 只有当 fabric_api 版本存在时才替换
    if data.get("fabric_api"):
        replacements[r'fabric_version=.*'] = f'fabric_version={data["fabric_api"]}'
    
    for pattern, repl in replacements.items():
        content = re.sub(pattern, repl, content)
    
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, f"gradle-{mc_ver}.properties")
    
    with open(output_file, 'w') as f:
        f.write(content)
    
    return output_file

def main():
    parser = argparse.ArgumentParser(
        description='Fabric版本配置生成器',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument('-l', '--list', action='store_true',
                      help='仅列出可用版本')
    parser.add_argument('-v', '--versions', nargs='+',
                      help='指定要生成的大版本号（如 1.21 1.20）')
    parser.add_argument('-t', '--template', default='..\gradle.properties',
                      help='模板文件路径')
    parser.add_argument('-o', '--output', default='versions',
                      help='输出目录')
    args = parser.parse_args()

    print("正在获取Fabric版本信息...")
    versions = get_stable_versions()
    
    if not versions:
        print("错误：无法获取有效的版本信息")
        return

    if args.list:
        print("\n可用版本（稳定版）:")
        major_versions = {}
        for ver, data in versions.items():
            major = data["major_version"]
            major_versions.setdefault(major, []).append(ver)
        
        for major in sorted(major_versions.keys(), key=version.parse, reverse=True):
            print(f"\n{major} 系列:")
            for ver in sorted(major_versions[major], key=version.parse, reverse=True):
                print(f"  {ver}")
        return

    # 筛选指定版本
    if args.versions:
        filtered_versions = {
            ver: data for ver, data in versions.items() 
            if data["major_version"] in args.versions
        }
        if not filtered_versions:
            print(f"错误：找不到指定版本 {args.versions}")
            return
        versions = filtered_versions

    # 检查模板文件
    if not os.path.exists(args.template):
        print(f"错误：模板文件不存在 {args.template}")
        return

    # 生成配置文件
    os.makedirs(args.output, exist_ok=True)
    print(f"\n正在为以下版本生成配置 ({len(versions)} 个):")
    
    success_count = 0
    for ver, data in versions.items():
        try:
            output_file = generate_properties_file(ver, data, args.template, args.output)
            print(f"✓ 已生成: {output_file}")
            success_count += 1
        except Exception as e:
            print(f"✗ 生成 {ver} 配置失败: {str(e)}")
    
    print(f"\n完成！成功生成 {success_count}/{len(versions)} 个配置文件")
    if success_count > 0:
        print("\n构建命令示例:")
        for ver in versions:
            if ver in [k for k in versions.keys()][:3]:  # 只显示前3个示例
                prop_file = os.path.join(args.output, f"gradle-{ver}.properties")
                print(f"./gradlew build -Dorg.gradle.project.customPropertiesFile={prop_file}")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n操作被用户中断")
    except Exception as e:
        print(f"发生未预期错误: {str(e)}")