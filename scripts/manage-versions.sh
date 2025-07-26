#!/bin/bash

# Luminous LLM Chat 版本管理脚本
# 用于管理多版本构建和分支策略

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 检查Git状态
check_git_status() {
    if [ -n "$(git status --porcelain)" ]; then
        print_warning "工作目录有未提交的更改，请先提交或暂存更改"
        git status --short
        read -p "是否继续？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# 创建multi-version分支
create_multi_version_branch() {
    print_info "创建multi-version分支..."
    
    # 检查分支是否已存在
    if git show-ref --verify --quiet refs/heads/multi-version; then
        print_warning "multi-version分支已存在"
        read -p "是否要重置该分支？(y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git branch -D multi-version
            print_success "已删除旧的multi-version分支"
        else
            print_info "使用现有的multi-version分支"
            return
        fi
    fi
    
    # 从main分支创建multi-version分支
    git checkout main
    git pull origin main
    git checkout -b multi-version
    
    print_success "已创建multi-version分支"
}

# 推送multi-version分支
push_multi_version_branch() {
    local skip_sync=${1:-false}

    print_info "推送multi-version分支到远程仓库..."

    # 检查multi-version分支是否存在
    if ! git show-ref --verify --quiet refs/heads/multi-version; then
        print_error "multi-version分支不存在，请先运行 'create-branch' 命令创建分支"
        return 1
    fi

    # 除非明确跳过，否则先同步main分支的更改
    if [ "$skip_sync" != "true" ]; then
        print_info "推送前先同步main分支的最新更改..."
        sync_from_main
        if [ $? -ne 0 ]; then
            print_error "同步main分支失败，取消推送操作"
            return 1
        fi
    fi

    # 确保当前在multi-version分支
    current_branch=$(git branch --show-current)
    if [ "$current_branch" != "multi-version" ]; then
        git checkout multi-version
        if [ $? -ne 0 ]; then
            print_error "无法切换到multi-version分支"
            return 1
        fi
    fi

    # 推送分支
    print_info "推送multi-version分支..."
    git push -u origin multi-version
    if [ $? -eq 0 ]; then
        print_success "multi-version分支已推送到远程仓库"
        print_info "GitHub Actions将自动开始多版本构建"
        print_info "查看构建状态: https://github.com/rice-awa/Luminous-llmchat/actions"
    else
        print_error "推送multi-version分支失败"
        return 1
    fi
}

# 自动发现可用版本
get_available_versions() {
    declare -A versions

    # 扫描build_version目录下的配置文件
    if [ -d "build_version" ]; then
        while IFS= read -r -d '' file; do
            # 从文件名提取版本号 (gradle-1.21.5.properties -> 1.21.5)
            filename=$(basename "$file")
            if [[ $filename =~ gradle-([0-9]+\.[0-9]+\.[0-9]+)\.properties ]]; then
                version="${BASH_REMATCH[1]}"
                versions["$version"]="$file"
            fi
        done < <(find build_version -name "gradle-*.properties" -type f -print0)
    fi

    # 添加默认版本（使用根目录的gradle.properties）
    if [ -f "gradle.properties" ]; then
        default_version=$(grep "minecraft_version=" gradle.properties | cut -d'=' -f2)
        if [ -n "$default_version" ]; then
            versions["$default_version"]="gradle.properties"
        fi
    fi

    # 输出版本信息（用于其他函数调用）
    for version in "${!versions[@]}"; do
        echo "$version:${versions[$version]}"
    done
}

# 列出可用版本
list_versions() {
    print_info "自动扫描可用的Minecraft版本:"

    declare -A versions
    while IFS=':' read -r version file; do
        versions["$version"]="$file"
    done < <(get_available_versions)

    if [ ${#versions[@]} -eq 0 ]; then
        print_warning "未发现任何版本配置文件"
        return
    fi

    # 按版本号排序显示
    for version in $(printf '%s\n' "${!versions[@]}" | sort -V -r); do
        file="${versions[$version]}"
        relative_path="${file#$(pwd)/}"
        if [ "$file" = "gradle.properties" ]; then
            echo "  • $version ($relative_path - 默认开发版本)"
        else
            echo "  • $version ($relative_path)"
        fi
    done

    print_info "总共发现 ${#versions[@]} 个版本配置"
}

# 本地测试构建
test_build_version() {
    local version=$1
    if [ -z "$version" ]; then
        print_error "请指定要测试的版本"
        list_versions
        return 1
    fi

    # 获取可用版本
    declare -A available_versions
    while IFS=':' read -r ver file; do
        available_versions["$ver"]="$file"
    done < <(get_available_versions)

    if [ -z "${available_versions[$version]}" ]; then
        print_error "不支持的版本: $version"
        print_info "可用版本:"
        list_versions
        return 1
    fi

    print_info "测试构建Minecraft $version..."

    # 备份当前gradle.properties
    cp gradle.properties gradle.properties.backup

    # 获取版本对应的配置文件
    config_file="${available_versions[$version]}"

    if [ "$config_file" != "gradle.properties" ]; then
        print_info "使用配置文件: $config_file"
        cp "$config_file" gradle.properties
    else
        print_info "使用默认配置文件: gradle.properties"
    fi

    print_info "当前构建配置:"
    grep -E "(minecraft_version|mod_version|fabric_version)" gradle.properties

    # 运行构建
    print_info "开始构建..."
    if ./gradlew clean build; then
        print_success "Minecraft $version 构建成功！"
        ls -la build/libs/
    else
        print_error "Minecraft $version 构建失败"
        mv gradle.properties.backup gradle.properties
        return 1
    fi

    # 恢复原配置
    mv gradle.properties.backup gradle.properties
    print_info "已恢复原始gradle.properties配置"
}

# 同步main分支的更改到multi-version分支
sync_from_main() {
    print_info "将main分支的更改同步到multi-version分支..."
    
    # 保存当前分支
    current_branch=$(git branch --show-current)
    
    # 切换到main分支并拉取最新更改
    git checkout main
    git pull origin main
    
    # 切换到multi-version分支并合并main的更改
    git checkout multi-version
    git merge main --no-edit
    
    print_success "已将main分支的更改合并到multi-version分支"
    
    # 如果原来不在multi-version分支，切换回原分支
    if [ "$current_branch" != "multi-version" ]; then
        git checkout "$current_branch"
    fi
}

# 完整的发布流程
start_release() {
    print_info "开始完整的发布流程..."
    print_info "这将执行以下步骤："
    print_info "1. 同步main分支的最新更改"
    print_info "2. 推送multi-version分支"
    print_info "3. 触发GitHub Actions多版本构建"
    echo ""

    read -p "是否继续？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "发布流程已取消"
        return
    fi

    # 检查multi-version分支是否存在
    if ! git show-ref --verify --quiet refs/heads/multi-version; then
        print_warning "multi-version分支不存在，正在创建..."
        create_multi_version_branch
        if [ $? -ne 0 ]; then
            print_error "创建multi-version分支失败，发布流程终止"
            return 1
        fi
    fi

    # 执行发布流程
    print_info "步骤 1/3: 同步main分支的更改..."
    sync_from_main
    if [ $? -ne 0 ]; then
        print_error "同步main分支失败，发布流程终止"
        return 1
    fi

    print_info "步骤 2/3: 推送multi-version分支..."
    push_multi_version_branch true  # 跳过同步，因为刚刚已经同步过了
    if [ $? -ne 0 ]; then
        print_error "推送multi-version分支失败，发布流程终止"
        return 1
    fi

    print_info "步骤 3/3: 构建已触发"
    print_success "🎉 发布流程完成！"
    echo ""
    print_info "接下来："
    print_info "• 查看GitHub Actions构建状态"
    print_info "• 等待所有版本构建完成"
    print_info "• 下载构建产物或创建Release"
    echo ""
    print_info "GitHub Actions: https://github.com/rice-awa/Luminous-llmchat/actions"
}

# 显示帮助信息
show_help() {
    echo "Luminous LLM Chat 版本管理脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  create-branch    创建multi-version分支"
    echo "  push-branch      推送multi-version分支到远程（自动同步main）"
    echo "  list-versions    列出所有可用版本"
    echo "  test-build <版本> 本地测试构建指定版本"
    echo "  sync-from-main   将main分支的更改同步到multi-version分支"
    echo "  release          完整的发布流程（同步+推送+触发构建）"
    echo "  help             显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 create-branch"
    echo "  $0 test-build 1.21.6"
    echo "  $0 sync-from-main"
}

# 主函数
main() {
    case "${1:-help}" in
        "create-branch")
            check_git_status
            create_multi_version_branch
            ;;
        "push-branch")
            push_multi_version_branch
            ;;
        "list-versions")
            list_versions
            ;;
        "test-build")
            test_build_version "$2"
            ;;
        "sync-from-main")
            check_git_status
            sync_from_main
            ;;
        "release")
            check_git_status
            start_release
            ;;
        "help"|*)
            show_help
            ;;
    esac
}

# 检查是否在项目根目录
if [ ! -f "gradle.properties" ] || [ ! -f "build.gradle" ]; then
    print_error "请在项目根目录运行此脚本"
    exit 1
fi

main "$@"
