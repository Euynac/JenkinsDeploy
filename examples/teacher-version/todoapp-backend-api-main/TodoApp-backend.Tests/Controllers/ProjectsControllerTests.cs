using System.Security.Claims;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TodoApp_backend.Controllers;
using TodoApp_backend.Data;
using TodoApp_backend.DTOs;
using TodoApp_backend.Models;
using Xunit;

namespace TodoApp_backend.Tests.Controllers;

public class ProjectsControllerTests
{
    private ApplicationDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<ApplicationDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        return new ApplicationDbContext(options);
    }

    private ProjectsController CreateController(ApplicationDbContext context, int userId)
    {
        var controller = new ProjectsController(context);
        
        // 设置用户身份
        var claims = new List<Claim>
        {
            new Claim(ClaimTypes.NameIdentifier, userId.ToString()),
            new Claim(ClaimTypes.Name, "testuser")
        };
        var identity = new ClaimsIdentity(claims, "TestAuth");
        var principal = new ClaimsPrincipal(identity);
        
        controller.ControllerContext = new Microsoft.AspNetCore.Mvc.ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = principal
            }
        };
        
        return controller;
    }

    [Fact]
    public async Task GetProjects_ShouldReturnOnlyUserProjects()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId1 = 1;
        var userId2 = 2;
        
        // 添加不同用户的项目
        context.Projects.AddRange(
            new Project { Name = "User1 Project", UserId = userId1 },
            new Project { Name = "User1 Project 2", UserId = userId1 },
            new Project { Name = "User2 Project", UserId = userId2 }
        );
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId1);

        // Act
        var result = await controller.GetProjects();

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var pagedResult = okResult!.Value as PagedResultDto<ProjectDto>;
        pagedResult.Should().NotBeNull();
        pagedResult!.Items.Should().HaveCount(2);
        pagedResult.Items.Should().OnlyContain(p => p.Name.Contains("User1"));
    }

    [Fact]
    public async Task GetProjects_ShouldSupportPagination()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId = 1;
        
        // 添加多个项目
        for (int i = 1; i <= 15; i++)
        {
            context.Projects.Add(new Project 
            { 
                Name = $"Project {i}", 
                UserId = userId,
                CreatedAt = DateTime.UtcNow.AddMinutes(-i)
            });
        }
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.GetProjects(pageNumber: 1, pageSize: 10);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var pagedResult = okResult!.Value as PagedResultDto<ProjectDto>;
        pagedResult.Should().NotBeNull();
        pagedResult!.Items.Should().HaveCount(10);
        pagedResult.TotalCount.Should().Be(15);
        pagedResult.PageNumber.Should().Be(1);
        pagedResult.PageSize.Should().Be(10);
    }

    [Fact]
    public async Task GetProject_ShouldReturnProject_WhenProjectExistsAndBelongsToUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId = 1;
        
        var project = new Project
        {
            Name = "Test Project",
            Description = "Test Description",
            UserId = userId
        };
        context.Projects.Add(project);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.GetProject(project.Id);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var projectDto = okResult!.Value as ProjectDto;
        projectDto.Should().NotBeNull();
        projectDto!.Name.Should().Be("Test Project");
        projectDto.Description.Should().Be("Test Description");
    }

    [Fact]
    public async Task GetProject_ShouldReturnNotFound_WhenProjectDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.GetProject(999);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task GetProject_ShouldReturnNotFound_WhenProjectBelongsToDifferentUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId1 = 1;
        var userId2 = 2;
        
        var project = new Project
        {
            Name = "Other User Project",
            UserId = userId2
        };
        context.Projects.Add(project);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId1);

        // Act
        var result = await controller.GetProject(project.Id);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task CreateProject_ShouldCreateProject()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);
        
        var createDto = new CreateProjectDto
        {
            Name = "New Project",
            Description = "New Description"
        };

        // Act
        var result = await controller.CreateProject(createDto);

        // Assert
        result.Result.Should().BeOfType<CreatedAtActionResult>();
        var createdAtResult = result.Result as CreatedAtActionResult;
        var projectDto = createdAtResult!.Value as ProjectDto;
        projectDto.Should().NotBeNull();
        projectDto!.Name.Should().Be("New Project");
        projectDto.Description.Should().Be("New Description");
        
        // 验证已保存到数据库
        var project = await context.Projects.FirstOrDefaultAsync(p => p.Name == "New Project");
        project.Should().NotBeNull();
        project!.UserId.Should().Be(1);
    }

    [Fact]
    public async Task UpdateProject_ShouldUpdateProject_WhenProjectExistsAndBelongsToUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId = 1;
        
        var project = new Project
        {
            Name = "Original Name",
            Description = "Original Description",
            UserId = userId
        };
        context.Projects.Add(project);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);
        var updateDto = new UpdateProjectDto
        {
            Name = "Updated Name",
            Description = "Updated Description"
        };

        // Act
        var result = await controller.UpdateProject(project.Id, updateDto);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var projectDto = okResult!.Value as ProjectDto;
        projectDto.Should().NotBeNull();
        projectDto!.Name.Should().Be("Updated Name");
        projectDto.Description.Should().Be("Updated Description");
        
        // 验证数据库已更新
        var updatedProject = await context.Projects.FindAsync(project.Id);
        updatedProject.Should().NotBeNull();
        updatedProject!.Name.Should().Be("Updated Name");
    }

    [Fact]
    public async Task UpdateProject_ShouldReturnNotFound_WhenProjectDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);
        var updateDto = new UpdateProjectDto { Name = "Test", Description = "Test" };

        // Act
        var result = await controller.UpdateProject(999, updateDto);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task DeleteProject_ShouldDeleteProject_WhenProjectExistsAndBelongsToUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId = 1;
        
        var project = new Project
        {
            Name = "To Delete",
            UserId = userId
        };
        context.Projects.Add(project);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.DeleteProject(project.Id);

        // Assert
        result.Should().BeOfType<NoContentResult>();
        
        // 验证已从数据库删除
        var deletedProject = await context.Projects.FindAsync(project.Id);
        deletedProject.Should().BeNull();
    }

    [Fact]
    public async Task DeleteProject_ShouldReturnNotFound_WhenProjectDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.DeleteProject(999);

        // Assert
        result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task GetProjectTodos_ShouldReturnTodos_WhenProjectExistsAndBelongsToUser()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var userId = 1;
        
        var project = new Project
        {
            Name = "Test Project",
            UserId = userId
        };
        context.Projects.Add(project);
        await context.SaveChangesAsync();
        
        context.Todos.AddRange(
            new Todo { Title = "Todo 1", ProjectId = project.Id },
            new Todo { Title = "Todo 2", ProjectId = project.Id }
        );
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.GetProjectTodos(project.Id);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var todos = okResult!.Value as List<TodoDto>;
        todos.Should().NotBeNull();
        todos!.Should().HaveCount(2);
    }

    [Fact]
    public async Task GetProjectTodos_ShouldReturnNotFound_WhenProjectDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.GetProjectTodos(999);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }
}

