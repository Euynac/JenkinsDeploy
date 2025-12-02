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

public class TodosControllerTests
{
    private ApplicationDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<ApplicationDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        return new ApplicationDbContext(options);
    }

    private TodosController CreateController(ApplicationDbContext context, int userId)
    {
        var controller = new TodosController(context);
        
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
    public async Task CreateTodo_ShouldCreateTodo_WhenProjectExistsAndBelongsToUser()
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
        
        var controller = CreateController(context, userId);
        var createDto = new CreateTodoDto
        {
            Title = "New Todo",
            Description = "New Description",
            ProjectId = project.Id
        };

        // Act
        var result = await controller.CreateTodo(createDto);

        // Assert
        result.Result.Should().BeOfType<CreatedAtActionResult>();
        var createdAtResult = result.Result as CreatedAtActionResult;
        var todoDto = createdAtResult!.Value as TodoDto;
        todoDto.Should().NotBeNull();
        todoDto!.Title.Should().Be("New Todo");
        todoDto.Description.Should().Be("New Description");
        todoDto.ProjectId.Should().Be(project.Id);
        todoDto.IsCompleted.Should().BeFalse();
        
        // 验证已保存到数据库
        var todo = await context.Todos.FirstOrDefaultAsync(t => t.Title == "New Todo");
        todo.Should().NotBeNull();
    }

    [Fact]
    public async Task CreateTodo_ShouldReturnNotFound_WhenProjectDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);
        var createDto = new CreateTodoDto
        {
            Title = "New Todo",
            ProjectId = 999
        };

        // Act
        var result = await controller.CreateTodo(createDto);

        // Assert
        result.Result.Should().BeOfType<NotFoundObjectResult>();
    }

    [Fact]
    public async Task CreateTodo_ShouldReturnNotFound_WhenProjectBelongsToDifferentUser()
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
        var createDto = new CreateTodoDto
        {
            Title = "New Todo",
            ProjectId = project.Id
        };

        // Act
        var result = await controller.CreateTodo(createDto);

        // Assert
        result.Result.Should().BeOfType<NotFoundObjectResult>();
    }

    [Fact]
    public async Task GetTodo_ShouldReturnTodo_WhenTodoExistsAndBelongsToUser()
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
        
        var todo = new Todo
        {
            Title = "Test Todo",
            Description = "Test Description",
            ProjectId = project.Id
        };
        context.Todos.Add(todo);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.GetTodo(todo.Id);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var todoDto = okResult!.Value as TodoDto;
        todoDto.Should().NotBeNull();
        todoDto!.Title.Should().Be("Test Todo");
        todoDto.Description.Should().Be("Test Description");
    }

    [Fact]
    public async Task GetTodo_ShouldReturnNotFound_WhenTodoDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.GetTodo(999);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task GetTodo_ShouldReturnNotFound_WhenTodoBelongsToDifferentUser()
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
        
        var todo = new Todo
        {
            Title = "Other User Todo",
            ProjectId = project.Id
        };
        context.Todos.Add(todo);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId1);

        // Act
        var result = await controller.GetTodo(todo.Id);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task UpdateTodo_ShouldUpdateTodo_WhenTodoExistsAndBelongsToUser()
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
        
        var todo = new Todo
        {
            Title = "Original Title",
            Description = "Original Description",
            ProjectId = project.Id
        };
        context.Todos.Add(todo);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);
        var updateDto = new UpdateTodoDto
        {
            Title = "Updated Title",
            Description = "Updated Description"
        };

        // Act
        var result = await controller.UpdateTodo(todo.Id, updateDto);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var todoDto = okResult!.Value as TodoDto;
        todoDto.Should().NotBeNull();
        todoDto!.Title.Should().Be("Updated Title");
        todoDto.Description.Should().Be("Updated Description");
        
        // 验证数据库已更新
        var updatedTodo = await context.Todos.FindAsync(todo.Id);
        updatedTodo.Should().NotBeNull();
        updatedTodo!.Title.Should().Be("Updated Title");
    }

    [Fact]
    public async Task UpdateTodo_ShouldReturnNotFound_WhenTodoDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);
        var updateDto = new UpdateTodoDto { Title = "Test", Description = "Test" };

        // Act
        var result = await controller.UpdateTodo(999, updateDto);

        // Assert
        result.Result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task DeleteTodo_ShouldDeleteTodo_WhenTodoExistsAndBelongsToUser()
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
        
        var todo = new Todo
        {
            Title = "To Delete",
            ProjectId = project.Id
        };
        context.Todos.Add(todo);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.DeleteTodo(todo.Id);

        // Assert
        result.Should().BeOfType<NoContentResult>();
        
        // 验证已从数据库删除
        var deletedTodo = await context.Todos.FindAsync(todo.Id);
        deletedTodo.Should().BeNull();
    }

    [Fact]
    public async Task DeleteTodo_ShouldReturnNotFound_WhenTodoDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.DeleteTodo(999);

        // Assert
        result.Should().BeOfType<NotFoundResult>();
    }

    [Fact]
    public async Task ToggleComplete_ShouldToggleCompletionStatus()
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
        
        var todo = new Todo
        {
            Title = "Test Todo",
            IsCompleted = false,
            ProjectId = project.Id
        };
        context.Todos.Add(todo);
        await context.SaveChangesAsync();
        
        var controller = CreateController(context, userId);

        // Act
        var result = await controller.ToggleComplete(todo.Id);

        // Assert
        result.Should().BeOfType<OkObjectResult>();
        var okResult = result as OkObjectResult;
        var todoDto = okResult!.Value as TodoDto;
        todoDto.Should().NotBeNull();
        todoDto!.IsCompleted.Should().BeTrue();
        
        // 验证数据库已更新
        var updatedTodo = await context.Todos.FindAsync(todo.Id);
        updatedTodo.Should().NotBeNull();
        updatedTodo!.IsCompleted.Should().BeTrue();
    }

    [Fact]
    public async Task ToggleComplete_ShouldReturnNotFound_WhenTodoDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var controller = CreateController(context, 1);

        // Act
        var result = await controller.ToggleComplete(999);

        // Assert
        result.Should().BeOfType<NotFoundResult>();
    }
}

