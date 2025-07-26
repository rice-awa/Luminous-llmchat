#!/bin/bash

# Luminous LLM Chat 自动同步和构建脚本
# 自动完成：更新main分支 -> 合并到multi-version -> 触发构建

set -e  # 遇到错误时退出

# 默认参数
FORCE=false
DRY_RUN=false
SKIP_TESTS=false
VERBOSE=false
TEST_VERSION="1.21.7"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 输出函数
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

print_step() {
    echo -e "${CYAN}🔄 $1${NC}"
}

# 显示帮助信息
show_help() {
    echo "Luminous LLM Chat 自动同步和构建脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -f, --force         强制执行，跳过确认提示"
    echo "  -d, --dry-run       预演模式，显示将要执行的操作但不实际执行"
    echo "  -s, --skip-tests    跳过本地测试构建"
    echo "  -v, --verbose       显示详细输出"
    echo "  -t, --test-version  指定用于测试的版本 (默认: 1.21.7)"
    echo "  -h, --help          显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                           # 标准执行"
    echo "  $0 --force                   # 强制执行，跳过确认"
    echo "  $0 --dry-run                 # 预演模式"
    echo "  $0 --skip-tests              # 跳过本地测试"
    echo "  $0 --test-version 1.21.6     # 使用特定版本测试"
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--force)
                FORCE=true
                shift
                ;;
            -d|--dry-run)
                DRY_RUN=true
                shift
                ;;
            -s|--skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -t|--test-version)
                TEST_VERSION="$2"
                shift 2
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 检查Git状态
check_git_status() {
    local status=$(git status --porcelain)
    if [ -n "$status" ]; then
        print_warning "工作目录有未提交的更改："
        git status --short
        
        if [ "$FORCE" != "true" ]; then
            read -p "是否继续？这将暂存当前更改 (y/N): " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                print_info "操作已取消"
                exit 1
            fi
        fi
        
        print_info "暂存当前更改..."
        if [ "$DRY_RUN" != "true" ]; then
            git stash push -m "Auto-sync script stash $(date '+%Y-%m-%d %H:%M:%S')"
        fi
        print_success "已暂存未提交的更改"
    fi
}

# 检查分支是否存在
branch_exists() {
    git show-ref --verify --quiet "refs/heads/$1"
}

# 更新main分支
update_main_branch() {
    print_step "步骤 1/5: 更新main分支"
    
    # 保存当前分支
    local current_branch=$(git branch --show-current)
    print_info "当前分支: $current_branch"
    
    # 切换到main分支
    print_info "切换到main分支..."
    if [ "$DRY_RUN" != "true" ]; then
        git checkout main
    fi
    
    # 拉取最新更改
    print_info "拉取main分支的最新更改..."
    if [ "$DRY_RUN" != "true" ]; then
        git pull origin main
    fi
    
    # 显示最新提交
    if [ "$VERBOSE" = "true" ]; then
        print_info "main分支最新提交："
        if [ "$DRY_RUN" != "true" ]; then
            git log --oneline -5
        else
            echo "  [DRY RUN] 将显示最新5个提交"
        fi
    fi
    
    print_success "main分支已更新到最新版本"
}

# 检查并创建multi-version分支
ensure_multi_version_branch() {
    print_step "步骤 2/5: 检查multi-version分支"
    
    if branch_exists "multi-version"; then
        print_success "multi-version分支已存在"
        return 0
    fi
    
    print_warning "multi-version分支不存在，正在创建..."
    if [ "$FORCE" != "true" ]; then
        read -p "是否创建multi-version分支？(y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_error "需要multi-version分支才能继续"
            return 1
        fi
    fi
    
    if [ "$DRY_RUN" != "true" ]; then
        git checkout -b multi-version
        git push -u origin multi-version
    fi
    
    print_success "multi-version分支已创建"
}

# 合并main分支到multi-version
merge_main_to_multi_version() {
    print_step "步骤 3/5: 合并main分支到multi-version"
    
    # 切换到multi-version分支
    print_info "切换到multi-version分支..."
    if [ "$DRY_RUN" != "true" ]; then
        git checkout multi-version
    fi
    
    # 检查是否需要合并
    if [ "$DRY_RUN" != "true" ]; then
        local behind_commits=$(git rev-list --count HEAD..main)
        if [ "$behind_commits" = "0" ]; then
            print_success "multi-version分支已是最新，无需合并"
            return 0
        fi
        print_info "需要合并 $behind_commits 个提交"
    fi
    
    # 合并main分支
    print_info "合并main分支的更改..."
    if [ "$DRY_RUN" != "true" ]; then
        if ! git merge main --no-edit; then
            print_error "合并过程中出现冲突，请手动解决冲突后重新运行脚本"
            print_info "解决冲突的步骤："
            print_info "1. 编辑冲突文件，解决冲突标记"
            print_info "2. git add ."
            print_info "3. git commit"
            print_info "4. 重新运行此脚本"
            return 1
        fi
    fi
    
    print_success "已成功合并main分支的更改"
}

# 运行本地测试
test_local_build() {
    print_step "步骤 4/5: 运行本地测试构建"
    
    if [ "$SKIP_TESTS" = "true" ]; then
        print_info "跳过本地测试（使用了 --skip-tests 参数）"
        return 0
    fi
    
    print_info "运行本地测试构建（版本: $TEST_VERSION）..."
    
    if [ "$DRY_RUN" != "true" ]; then
        # 检查测试脚本是否存在
        if [ -f "scripts/manage-versions.sh" ]; then
            if ! ./scripts/manage-versions.sh test-build "$TEST_VERSION"; then
                print_error "本地测试构建失败"
                read -p "是否继续推送？(y/N): " -n 1 -r
                echo
                if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                    return 1
                fi
            else
                print_success "本地测试构建成功"
            fi
        else
            print_warning "未找到测试脚本，跳过本地测试"
        fi
    else
        echo "  [DRY RUN] 将运行本地测试构建"
    fi
}

# 推送并触发构建
push_and_trigger_build() {
    print_step "步骤 5/5: 推送并触发GitHub Actions构建"
    
    print_info "推送multi-version分支到远程仓库..."
    if [ "$DRY_RUN" != "true" ]; then
        git push origin multi-version
    fi
    
    print_success "已推送multi-version分支，GitHub Actions将自动开始多版本构建"
    
    # 显示GitHub Actions链接
    local repo_url=$(git config --get remote.origin.url)
    if [[ $repo_url =~ github\.com[:/](.+)/(.+)\.git ]]; then
        local owner="${BASH_REMATCH[1]}"
        local repo="${BASH_REMATCH[2]}"
        local actions_url="https://github.com/$owner/$repo/actions"
        print_info "查看构建状态: $actions_url"
    fi
}

# 显示摘要
show_summary() {
    local success=$1
    
    echo ""
    echo "=================================================="
    
    if [ "$success" = "true" ]; then
        print_success "🎉 自动同步和构建流程完成！"
        echo ""
        print_info "已完成的操作："
        echo "  ✅ 更新了main分支到最新版本"
        echo "  ✅ 合并main分支到multi-version分支"
        if [ "$SKIP_TESTS" != "true" ]; then
            echo "  ✅ 运行了本地测试构建"
        fi
        echo "  ✅ 推送multi-version分支触发GitHub Actions"
        echo ""
        print_info "接下来："
        echo "  • 查看GitHub Actions构建状态"
        echo "  • 等待所有版本构建完成"
        echo "  • 下载构建产物或创建Release"
    else
        print_error "❌ 自动同步和构建流程失败"
        echo ""
        print_info "请检查上述错误信息并手动解决问题"
    fi
    
    echo "=================================================="
}

# 主函数
main() {
    # 检查是否在项目根目录
    if [ ! -f "gradle.properties" ] || [ ! -f "build.gradle" ]; then
        print_error "请在项目根目录运行此脚本"
        exit 1
    fi
    
    echo -e "${YELLOW}🚀 Luminous LLM Chat 自动同步和构建脚本${NC}"
    echo "=================================================="
    
    if [ "$DRY_RUN" = "true" ]; then
        print_warning "预演模式：将显示要执行的操作但不实际执行"
    fi
    
    # 执行流程
    local success=true
    
    # 检查Git状态
    check_git_status
    
    # 执行各个步骤
    if ! update_main_branch; then success=false; fi
    if [ "$success" = "true" ] && ! ensure_multi_version_branch; then success=false; fi
    if [ "$success" = "true" ] && ! merge_main_to_multi_version; then success=false; fi
    if [ "$success" = "true" ] && ! test_local_build; then success=false; fi
    if [ "$success" = "true" ] && ! push_and_trigger_build; then success=false; fi
    
    # 显示摘要
    show_summary "$success"
    
    if [ "$success" != "true" ]; then
        exit 1
    fi
}

# 解析参数并运行
parse_args "$@"
main
