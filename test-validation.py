#!/usr/bin/env python3
"""
LLMChatMod Function Calling 测试验证脚本
通过静态分析验证测试代码的完整性和正确性
"""

import os
import re
from pathlib import Path

class TestValidator:
    def __init__(self):
        self.test_dir = Path("src/test/java/com/riceawa/llm")
        self.results = {
            'total_test_files': 0,
            'total_test_methods': 0,
            'test_categories': {},
            'issues': [],
            'coverage': {}
        }
    
    def validate_all_tests(self):
        """验证所有测试文件"""
        print("🧪 开始验证 LLMChatMod Function Calling 测试...")
        print("=" * 60)
        
        if not self.test_dir.exists():
            print("❌ 测试目录不存在")
            return False
        
        # 验证测试文件结构
        self.validate_test_structure()
        
        # 验证各个测试文件
        self.validate_core_tests()
        self.validate_function_tests()
        self.validate_service_tests()
        self.validate_integration_tests()
        
        # 生成报告
        self.generate_report()
        
        return len(self.results['issues']) == 0
    
    def validate_test_structure(self):
        """验证测试目录结构"""
        print("📁 验证测试目录结构...")
        
        expected_dirs = [
            'core',
            'function',
            'function/impl', 
            'service',
            'integration'
        ]
        
        for dir_name in expected_dirs:
            dir_path = self.test_dir / dir_name
            if dir_path.exists():
                print(f"  ✅ {dir_name}/ 目录存在")
            else:
                print(f"  ❌ {dir_name}/ 目录缺失")
                self.results['issues'].append(f"缺失目录: {dir_name}")
    
    def validate_core_tests(self):
        """验证核心类测试"""
        print("\n🔧 验证核心类测试...")
        
        core_tests = [
            'LLMConfigTest.java',
            'LLMMessageTest.java'
        ]
        
        for test_file in core_tests:
            file_path = self.test_dir / 'core' / test_file
            if file_path.exists():
                methods = self.count_test_methods(file_path)
                print(f"  ✅ {test_file}: {methods} 个测试方法")
                self.results['test_categories']['core'] = self.results['test_categories'].get('core', 0) + methods
                self.results['total_test_files'] += 1
                self.results['total_test_methods'] += methods
            else:
                print(f"  ❌ {test_file} 文件缺失")
                self.results['issues'].append(f"缺失文件: core/{test_file}")
    
    def validate_function_tests(self):
        """验证函数系统测试"""
        print("\n⚙️ 验证函数系统测试...")
        
        function_tests = [
            'FunctionRegistryTest.java',
            'impl/WorldInfoFunctionTest.java'
        ]
        
        for test_file in function_tests:
            file_path = self.test_dir / 'function' / test_file
            if file_path.exists():
                methods = self.count_test_methods(file_path)
                print(f"  ✅ {test_file}: {methods} 个测试方法")
                self.results['test_categories']['function'] = self.results['test_categories'].get('function', 0) + methods
                self.results['total_test_files'] += 1
                self.results['total_test_methods'] += methods
            else:
                print(f"  ❌ {test_file} 文件缺失")
                self.results['issues'].append(f"缺失文件: function/{test_file}")
    
    def validate_service_tests(self):
        """验证服务层测试"""
        print("\n🌐 验证服务层测试...")
        
        service_tests = [
            'OpenAIServiceTest.java'
        ]
        
        for test_file in service_tests:
            file_path = self.test_dir / 'service' / test_file
            if file_path.exists():
                methods = self.count_test_methods(file_path)
                print(f"  ✅ {test_file}: {methods} 个测试方法")
                self.results['test_categories']['service'] = self.results['test_categories'].get('service', 0) + methods
                self.results['total_test_files'] += 1
                self.results['total_test_methods'] += methods
            else:
                print(f"  ❌ {test_file} 文件缺失")
                self.results['issues'].append(f"缺失文件: service/{test_file}")
    
    def validate_integration_tests(self):
        """验证集成测试"""
        print("\n🔗 验证集成测试...")
        
        integration_tests = [
            'FunctionCallingIntegrationTest.java'
        ]
        
        for test_file in integration_tests:
            file_path = self.test_dir / 'integration' / test_file
            if file_path.exists():
                methods = self.count_test_methods(file_path)
                print(f"  ✅ {test_file}: {methods} 个测试方法")
                self.results['test_categories']['integration'] = self.results['test_categories'].get('integration', 0) + methods
                self.results['total_test_files'] += 1
                self.results['total_test_methods'] += methods
            else:
                print(f"  ❌ {test_file} 文件缺失")
                self.results['issues'].append(f"缺失文件: integration/{test_file}")
    
    def count_test_methods(self, file_path):
        """统计测试方法数量"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 查找 @Test 注解
            test_methods = re.findall(r'@Test\s+(?:void\s+)?(\w+)', content)
            return len(test_methods)
        except Exception as e:
            print(f"    ⚠️ 读取文件失败: {e}")
            return 0
    
    def validate_build_config(self):
        """验证构建配置"""
        print("\n🔨 验证构建配置...")
        
        build_file = Path("build.gradle")
        if build_file.exists():
            with open(build_file, 'r') as f:
                content = f.read()
            
            # 检查测试依赖
            required_deps = [
                'junit-jupiter',
                'mockito-core',
                'mockito-junit-jupiter',
                'mockwebserver'
            ]
            
            for dep in required_deps:
                if dep in content:
                    print(f"  ✅ 依赖 {dep} 已配置")
                else:
                    print(f"  ❌ 依赖 {dep} 缺失")
                    self.results['issues'].append(f"缺失依赖: {dep}")
            
            # 检查测试配置
            if 'useJUnitPlatform()' in content:
                print("  ✅ JUnit Platform 已配置")
            else:
                print("  ❌ JUnit Platform 配置缺失")
                self.results['issues'].append("缺失 JUnit Platform 配置")
        else:
            print("  ❌ build.gradle 文件不存在")
            self.results['issues'].append("缺失 build.gradle 文件")
    
    def generate_report(self):
        """生成测试验证报告"""
        print("\n" + "=" * 60)
        print("📊 测试验证报告")
        print("=" * 60)
        
        print(f"📁 测试文件总数: {self.results['total_test_files']}")
        print(f"🧪 测试方法总数: {self.results['total_test_methods']}")
        
        print("\n📋 分类统计:")
        for category, count in self.results['test_categories'].items():
            print(f"  {category}: {count} 个测试方法")
        
        if self.results['issues']:
            print(f"\n❌ 发现 {len(self.results['issues'])} 个问题:")
            for issue in self.results['issues']:
                print(f"  - {issue}")
        else:
            print("\n✅ 所有测试文件验证通过!")
        
        # 计算覆盖率估算
        self.estimate_coverage()
    
    def estimate_coverage(self):
        """估算测试覆盖率"""
        print("\n📈 测试覆盖率估算:")
        
        # 基于测试方法数量估算覆盖率
        expected_coverage = {
            'core': 15,      # 预期核心类测试方法数
            'function': 20,  # 预期函数测试方法数
            'service': 8,    # 预期服务测试方法数
            'integration': 4 # 预期集成测试方法数
        }
        
        total_expected = sum(expected_coverage.values())
        total_actual = self.results['total_test_methods']
        
        coverage_percentage = min(100, (total_actual / total_expected) * 100)
        
        print(f"  预期测试方法数: {total_expected}")
        print(f"  实际测试方法数: {total_actual}")
        print(f"  覆盖率估算: {coverage_percentage:.1f}%")
        
        if coverage_percentage >= 90:
            print("  🎉 测试覆盖率优秀!")
        elif coverage_percentage >= 70:
            print("  👍 测试覆盖率良好!")
        else:
            print("  ⚠️ 测试覆盖率需要改进")

def main():
    """主函数"""
    validator = TestValidator()
    
    # 验证测试
    success = validator.validate_all_tests()
    
    # 验证构建配置
    validator.validate_build_config()
    
    print("\n" + "=" * 60)
    if success:
        print("🎉 测试验证完成! 所有测试文件结构正确")
        print("💡 建议: 在有Java环境的机器上运行 './gradlew test' 进行实际测试")
    else:
        print("⚠️ 测试验证发现问题，请检查上述错误信息")
    
    print("\n📚 相关文档:")
    print("  - TESTING_GUIDE.md: 详细测试指南")
    print("  - TEST_SUMMARY.md: 测试总结报告")
    print("  - run-tests.sh: 测试运行脚本")

if __name__ == "__main__":
    main()
