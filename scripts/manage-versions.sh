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
    print_info "推送multi-version分支到远程仓库..."
    
    git push -u origin multi-version
    
    print_success "multi-version分支已推送到远程仓库"
}

# 列出可用版本
list_versions() {
    print_info "可用的Minecraft版本:"
    echo "  • 1.21.5 (build_version/1.21/gradle-1.21.5.properties)"
    echo "  • 1.21.6 (build_version/1.21/gradle-1.21.6.properties)"
    echo "  • 1.21.7 (gradle.properties - 默认开发版本)"
    echo "  • 1.21.8 (build_version/1.21/gradle-1.21.8.properties)"
}

# 本地测试构建
test_build_version() {
    local version=$1
    if [ -z "$version" ]; then
        print_error "请指定要测试的版本"
        list_versions
        return 1
    fi
    
    print_info "测试构建Minecraft $version..."
    
    # 备份当前gradle.properties
    cp gradle.properties gradle.properties.backup
    
    # 根据版本选择配置文件
    case $version in
        "1.21.5")
            cp "build_version/1.21/gradle-1.21.5.properties" gradle.properties
            ;;
        "1.21.6")
            cp "build_version/1.21/gradle-1.21.6.properties" gradle.properties
            ;;
        "1.21.7")
            # 使用默认配置
            ;;
        "1.21.8")
            cp "build_version/1.21/gradle-1.21.8.properties" gradle.properties
            ;;
        *)
            print_error "不支持的版本: $version"
            list_versions
            mv gradle.properties.backup gradle.properties
            return 1
            ;;
    esac
    
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

# 显示帮助信息
show_help() {
    echo "Luminous LLM Chat 版本管理脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  create-branch    创建multi-version分支"
    echo "  push-branch      推送multi-version分支到远程"
    echo "  list-versions    列出所有可用版本"
    echo "  test-build <版本> 本地测试构建指定版本"
    echo "  sync-from-main   将main分支的更改同步到multi-version分支"
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
