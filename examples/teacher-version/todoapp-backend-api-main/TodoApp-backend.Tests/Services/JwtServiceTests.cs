using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Moq;
using TodoApp_backend.Services;
using Xunit;

namespace TodoApp_backend.Tests.Services;

public class JwtServiceTests
{
    private readonly Mock<IConfiguration> _mockConfiguration;
    private readonly JwtService _jwtService;

    public JwtServiceTests()
    {
        _mockConfiguration = new Mock<IConfiguration>();
        
        // 设置默认配置
        _mockConfiguration.Setup(c => c["Jwt:Key"]).Returns("YourSuperSecretKeyThatIsAtLeast32CharactersLong!");
        _mockConfiguration.Setup(c => c["Jwt:Issuer"]).Returns("TodoApp");
        _mockConfiguration.Setup(c => c["Jwt:Audience"]).Returns("TodoApp");
        
        _jwtService = new JwtService(_mockConfiguration.Object);
    }

    [Fact]
    public void GenerateToken_ShouldReturnValidToken()
    {
        // Arrange
        var userId = 1;
        var username = "testuser";

        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        token.Should().NotBeNullOrEmpty();
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        jsonToken.Should().NotBeNull();
    }

    [Fact]
    public void GenerateToken_ShouldContainCorrectClaims()
    {
        // Arrange
        var userId = 123;
        var username = "testuser";

        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        
        jsonToken.Claims.Should().Contain(c => c.Type == ClaimTypes.NameIdentifier && c.Value == userId.ToString());
        jsonToken.Claims.Should().Contain(c => c.Type == ClaimTypes.Name && c.Value == username);
    }

    [Fact]
    public void GenerateToken_ShouldHaveCorrectIssuer()
    {
        // Arrange
        var userId = 1;
        var username = "testuser";

        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        jsonToken.Issuer.Should().Be("TodoApp");
    }

    [Fact]
    public void GenerateToken_ShouldHaveCorrectAudience()
    {
        // Arrange
        var userId = 1;
        var username = "testuser";

        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        jsonToken.Audiences.Should().Contain("TodoApp");
    }

    [Fact]
    public void GenerateToken_ShouldExpireIn7Days()
    {
        // Arrange
        var userId = 1;
        var username = "testuser";
        var expectedExpiry = DateTime.UtcNow.AddDays(7);

        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        
        // 允许1分钟的误差
        jsonToken.ValidTo.Should().BeCloseTo(expectedExpiry, TimeSpan.FromMinutes(1));
    }

    [Fact]
    public void GenerateToken_ShouldThrowException_WhenJwtKeyNotConfigured()
    {
        // Arrange
        var mockConfig = new Mock<IConfiguration>();
        mockConfig.Setup(c => c["Jwt:Key"]).Returns((string?)null);
        mockConfig.Setup(c => c["Jwt:Issuer"]).Returns("TodoApp");
        mockConfig.Setup(c => c["Jwt:Audience"]).Returns("TodoApp");
        
        var service = new JwtService(mockConfig.Object);

        // Act & Assert
        var exception = Assert.Throws<InvalidOperationException>(() => 
            service.GenerateToken(1, "testuser"));
        
        exception.Message.Should().Contain("JWT Key not configured");
    }

    [Theory]
    [InlineData(1, "user1")]
    [InlineData(999, "admin")]
    [InlineData(0, "guest")]
    public void GenerateToken_ShouldWorkWithDifferentUserIds(int userId, string username)
    {
        // Act
        var token = _jwtService.GenerateToken(userId, username);

        // Assert
        token.Should().NotBeNullOrEmpty();
        var handler = new JwtSecurityTokenHandler();
        var jsonToken = handler.ReadJwtToken(token);
        jsonToken.Claims.Should().Contain(c => c.Type == ClaimTypes.NameIdentifier && c.Value == userId.ToString());
        jsonToken.Claims.Should().Contain(c => c.Type == ClaimTypes.Name && c.Value == username);
    }
}

