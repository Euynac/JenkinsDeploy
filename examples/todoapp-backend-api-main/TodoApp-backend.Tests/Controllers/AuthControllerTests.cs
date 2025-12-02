using FluentAssertions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Moq;
using TodoApp_backend.Controllers;
using TodoApp_backend.Data;
using TodoApp_backend.DTOs;
using TodoApp_backend.Models;
using TodoApp_backend.Services;
using Xunit;

namespace TodoApp_backend.Tests.Controllers;

public class AuthControllerTests
{
    private ApplicationDbContext GetInMemoryDbContext()
    {
        var options = new DbContextOptionsBuilder<ApplicationDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;
        return new ApplicationDbContext(options);
    }

    private JwtService GetJwtService()
    {
        var mockConfiguration = new Mock<Microsoft.Extensions.Configuration.IConfiguration>();
        mockConfiguration.Setup(c => c["Jwt:Key"]).Returns("YourSuperSecretKeyThatIsAtLeast32CharactersLong!");
        mockConfiguration.Setup(c => c["Jwt:Issuer"]).Returns("TodoApp");
        mockConfiguration.Setup(c => c["Jwt:Audience"]).Returns("TodoApp");
        return new JwtService(mockConfiguration.Object);
    }

    [Fact]
    public async Task Register_ShouldReturnOk_WhenUserDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        var controller = new AuthController(context, jwtService);
        
        var registerDto = new RegisterDto
        {
            Username = "newuser",
            Email = "newuser@example.com",
            Password = "password123"
        };

        // Act
        var result = await controller.Register(registerDto);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var response = okResult!.Value as AuthResponseDto;
        response.Should().NotBeNull();
        response!.Token.Should().NotBeNullOrEmpty();
        response.UserId.Should().BeGreaterThan(0);
        response.Username.Should().Be("newuser");
        
        // 验证用户已保存到数据库
        var user = await context.Users.FirstOrDefaultAsync(u => u.Username == "newuser");
        user.Should().NotBeNull();
        user!.Email.Should().Be("newuser@example.com");
    }

    [Fact]
    public async Task Register_ShouldReturnBadRequest_WhenUsernameExists()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        
        // 添加已存在的用户
        context.Users.Add(new User
        {
            Username = "existinguser",
            Email = "existing@example.com",
            PasswordHash = "hash"
        });
        await context.SaveChangesAsync();
        
        var controller = new AuthController(context, jwtService);
        var registerDto = new RegisterDto
        {
            Username = "existinguser",
            Email = "newemail@example.com",
            Password = "password123"
        };

        // Act
        var result = await controller.Register(registerDto);

        // Assert
        result.Result.Should().BeOfType<BadRequestObjectResult>();
        var badRequestResult = result.Result as BadRequestObjectResult;
        badRequestResult!.Value.Should().NotBeNull();
    }

    [Fact]
    public async Task Register_ShouldReturnBadRequest_WhenEmailExists()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        
        // 添加已存在的用户
        context.Users.Add(new User
        {
            Username = "user1",
            Email = "existing@example.com",
            PasswordHash = "hash"
        });
        await context.SaveChangesAsync();
        
        var controller = new AuthController(context, jwtService);
        var registerDto = new RegisterDto
        {
            Username = "newuser",
            Email = "existing@example.com",
            Password = "password123"
        };

        // Act
        var result = await controller.Register(registerDto);

        // Assert
        result.Result.Should().BeOfType<BadRequestObjectResult>();
        var badRequestResult = result.Result as BadRequestObjectResult;
        badRequestResult!.Value.Should().NotBeNull();
    }

    [Fact]
    public async Task Register_ShouldHashPassword()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        var controller = new AuthController(context, jwtService);
        
        var registerDto = new RegisterDto
        {
            Username = "testuser",
            Email = "test@example.com",
            Password = "password123"
        };

        // Act
        await controller.Register(registerDto);

        // Assert
        var user = await context.Users.FirstOrDefaultAsync(u => u.Username == "testuser");
        user.Should().NotBeNull();
        user!.PasswordHash.Should().NotBe("password123");
        user.PasswordHash.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task Login_ShouldReturnOk_WhenCredentialsAreValid()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        
        var password = "password123";
        var passwordHash = BCrypt.Net.BCrypt.HashPassword(password);
        
        context.Users.Add(new User
        {
            Username = "testuser",
            Email = "test@example.com",
            PasswordHash = passwordHash
        });
        await context.SaveChangesAsync();
        
        var controller = new AuthController(context, jwtService);
        var loginDto = new LoginDto
        {
            Username = "testuser",
            Password = password
        };

        // Act
        var result = await controller.Login(loginDto);

        // Assert
        result.Result.Should().BeOfType<OkObjectResult>();
        var okResult = result.Result as OkObjectResult;
        var response = okResult!.Value as AuthResponseDto;
        response.Should().NotBeNull();
        response!.Token.Should().NotBeNullOrEmpty();
        response.Username.Should().Be("testuser");
    }

    [Fact]
    public async Task Login_ShouldReturnUnauthorized_WhenUserDoesNotExist()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        var controller = new AuthController(context, jwtService);
        
        var loginDto = new LoginDto
        {
            Username = "nonexistent",
            Password = "password123"
        };

        // Act
        var result = await controller.Login(loginDto);

        // Assert
        result.Result.Should().BeOfType<UnauthorizedObjectResult>();
    }

    [Fact]
    public async Task Login_ShouldReturnUnauthorized_WhenPasswordIsIncorrect()
    {
        // Arrange
        using var context = GetInMemoryDbContext();
        var jwtService = GetJwtService();
        
        var passwordHash = BCrypt.Net.BCrypt.HashPassword("correctpassword");
        context.Users.Add(new User
        {
            Username = "testuser",
            Email = "test@example.com",
            PasswordHash = passwordHash
        });
        await context.SaveChangesAsync();
        
        var controller = new AuthController(context, jwtService);
        var loginDto = new LoginDto
        {
            Username = "testuser",
            Password = "wrongpassword"
        };

        // Act
        var result = await controller.Login(loginDto);

        // Assert
        result.Result.Should().BeOfType<UnauthorizedObjectResult>();
    }
}

