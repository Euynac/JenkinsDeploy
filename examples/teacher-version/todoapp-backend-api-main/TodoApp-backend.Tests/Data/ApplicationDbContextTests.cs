using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using TodoApp_backend.Data;
using TodoApp_backend.Models;
using Xunit;

namespace TodoApp_backend.Tests.Data;

public class ApplicationDbContextTests
{
    private ApplicationDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<ApplicationDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        return new ApplicationDbContext(options);
    }

    [Fact]
    public void SeedData_ShouldCreateSeedData_WhenDatabaseIsEmpty()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();

        // Assert
        var users = context.Users.ToList();
        users.Should().HaveCount(1);
        users[0].Username.Should().Be("admin");
        users[0].Email.Should().Be("admin@example.com");
        users[0].Id.Should().Be(1);

        var projects = context.Projects.ToList();
        projects.Should().HaveCount(2);
        projects.Should().Contain(p => p.Name == "工作项目");
        projects.Should().Contain(p => p.Name == "个人项目");
        projects.All(p => p.UserId == 1).Should().BeTrue();

        var todos = context.Todos.ToList();
        todos.Should().HaveCount(6);
        todos.Count(t => t.IsCompleted).Should().Be(2);
        todos.Count(t => !t.IsCompleted).Should().Be(4);
    }

    [Fact]
    public void SeedData_ShouldNotCreateSeedData_WhenUsersAlreadyExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        
        // 添加一个已存在的用户
        context.Users.Add(new User
        {
            Id = 10,
            Username = "existinguser",
            Email = "existing@example.com",
            PasswordHash = "hash"
        });
        context.SaveChanges();

        // Act
        context.SeedData();

        // Assert
        var users = context.Users.ToList();
        users.Should().HaveCount(1);
        users[0].Username.Should().Be("existinguser");
        
        // 验证没有创建种子数据
        var projects = context.Projects.ToList();
        projects.Should().BeEmpty();
        
        var todos = context.Todos.ToList();
        todos.Should().BeEmpty();
    }

    [Fact]
    public void SeedData_ShouldCreateCorrectUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();

        // Assert
        var user = context.Users.First();
        user.Username.Should().Be("admin");
        user.Email.Should().Be("admin@example.com");
        user.PasswordHash.Should().NotBeNullOrEmpty();
        user.CreatedAt.Should().BeCloseTo(DateTime.UtcNow, TimeSpan.FromMinutes(1));
    }

    [Fact]
    public void SeedData_ShouldCreateCorrectProjects()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();

        // Assert
        var projects = context.Projects.OrderBy(p => p.Id).ToList();
        projects.Should().HaveCount(2);
        
        var workProject = projects.First(p => p.Name == "工作项目");
        workProject.Description.Should().Be("日常工作相关的任务管理");
        workProject.UserId.Should().Be(1);
        
        var personalProject = projects.First(p => p.Name == "个人项目");
        personalProject.Description.Should().Be("个人生活相关的任务管理");
        personalProject.UserId.Should().Be(1);
    }

    [Fact]
    public void SeedData_ShouldCreateCorrectTodos()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();

        // Assert
        var todos = context.Todos.ToList();
        todos.Should().HaveCount(6);
        
        // 验证工作项目的 todos
        var workProject = context.Projects.First(p => p.Name == "工作项目");
        var workTodos = todos.Where(t => t.ProjectId == workProject.Id).ToList();
        workTodos.Should().HaveCount(3);
        workTodos.Should().Contain(t => t.Title == "完成项目文档" && !t.IsCompleted);
        workTodos.Should().Contain(t => t.Title == "代码审查" && t.IsCompleted);
        workTodos.Should().Contain(t => t.Title == "准备会议材料" && !t.IsCompleted);
        
        // 验证个人项目的 todos
        var personalProject = context.Projects.First(p => p.Name == "个人项目");
        var personalTodos = todos.Where(t => t.ProjectId == personalProject.Id).ToList();
        personalTodos.Should().HaveCount(3);
        personalTodos.Should().Contain(t => t.Title == "购买生活用品" && !t.IsCompleted);
        personalTodos.Should().Contain(t => t.Title == "阅读技术书籍" && t.IsCompleted);
        personalTodos.Should().Contain(t => t.Title == "运动健身" && !t.IsCompleted);
    }

    [Fact]
    public void SeedData_ShouldSetCorrectIds()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();

        // Assert
        var user = context.Users.First();
        user.Id.Should().Be(1);
        
        var projects = context.Projects.OrderBy(p => p.Id).ToList();
        projects[0].Id.Should().Be(1);
        projects[1].Id.Should().Be(2);
        
        var todos = context.Todos.OrderBy(t => t.Id).ToList();
        todos.Should().HaveCount(6);
        todos[0].Id.Should().Be(1);
        todos[5].Id.Should().Be(6);
    }

    [Fact]
    public void SeedData_CanBeCalledMultipleTimesSafely()
    {
        // Arrange
        using var context = GetInMemoryDbContext();

        // Act
        context.SeedData();
        context.SeedData(); // 再次调用

        // Assert
        var users = context.Users.ToList();
        users.Should().HaveCount(1); // 应该仍然只有一个用户
        
        var projects = context.Projects.ToList();
        projects.Should().HaveCount(2); // 应该仍然只有两个项目
    }
}

