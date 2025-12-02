using Microsoft.EntityFrameworkCore;
using TodoApp_backend.Models;
using BCrypt.Net;

namespace TodoApp_backend.Data;

public class ApplicationDbContext : DbContext
{
    public ApplicationDbContext(DbContextOptions<ApplicationDbContext> options)
        : base(options)
    {
    }

    public DbSet<User> Users { get; set; }
    public DbSet<Project> Projects { get; set; }
    public DbSet<Todo> Todos { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // User configuration
        modelBuilder.Entity<User>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.HasIndex(e => e.Username).IsUnique();
            entity.HasIndex(e => e.Email).IsUnique();
            entity.Property(e => e.Username).IsRequired().HasMaxLength(50);
            entity.Property(e => e.Email).IsRequired().HasMaxLength(100);
        });

        // Project configuration
        modelBuilder.Entity<Project>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Name).IsRequired().HasMaxLength(200);
            entity.HasOne(e => e.User)
                  .WithMany(u => u.Projects)
                  .HasForeignKey(e => e.UserId)
                  .OnDelete(DeleteBehavior.Cascade);
        });

        // Todo configuration
        modelBuilder.Entity<Todo>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Title).IsRequired().HasMaxLength(200);
            entity.HasOne(e => e.Project)
                  .WithMany(p => p.Todos)
                  .HasForeignKey(e => e.ProjectId)
                  .OnDelete(DeleteBehavior.Cascade);
        });
    }

    /// <summary>
    /// 初始化种子数据（仅在数据库为空时执行）
    /// </summary>
    public void SeedData()
    {
        var hasUsers = Users.Any();
        
        // 如果数据库中已有用户，则不添加种子数据
        if (hasUsers)
        {
            // 即使数据已存在，也检查并修复序列（防止之前版本遗留的问题）
            FixSequences();
            return;
        }

        // 创建测试用户
        var testUser = new User
        {
            Id = 1,
            Username = "admin",
            Email = "admin@example.com",
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("admin123"),
            CreatedAt = DateTime.UtcNow
        };

        Users.Add(testUser);
        SaveChanges();

        // 创建测试项目
        var workProject = new Project
        {
            Id = 1,
            Name = "工作项目",
            Description = "日常工作相关的任务管理",
            UserId = testUser.Id,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        var personalProject = new Project
        {
            Id = 2,
            Name = "个人项目",
            Description = "个人生活相关的任务管理",
            UserId = testUser.Id,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        Projects.AddRange(workProject, personalProject);
        SaveChanges();

        // 创建测试 Todo
        var todos = new List<Todo>
        {
            new Todo
            {
                Id = 1,
                Title = "完成项目文档",
                Description = "编写项目技术文档和使用说明",
                IsCompleted = false,
                ProjectId = workProject.Id,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            },
            new Todo
            {
                Id = 2,
                Title = "代码审查",
                Description = "审查团队成员的代码提交",
                IsCompleted = true,
                ProjectId = workProject.Id,
                CreatedAt = DateTime.UtcNow.AddDays(-1),
                UpdatedAt = DateTime.UtcNow
            },
            new Todo
            {
                Id = 3,
                Title = "准备会议材料",
                Description = "准备下周团队会议的演示材料",
                IsCompleted = false,
                ProjectId = workProject.Id,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            },
            new Todo
            {
                Id = 4,
                Title = "购买生活用品",
                Description = "去超市购买日常用品",
                IsCompleted = false,
                ProjectId = personalProject.Id,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            },
            new Todo
            {
                Id = 5,
                Title = "阅读技术书籍",
                Description = "阅读《Clean Code》第3章",
                IsCompleted = true,
                ProjectId = personalProject.Id,
                CreatedAt = DateTime.UtcNow.AddDays(-2),
                UpdatedAt = DateTime.UtcNow
            },
            new Todo
            {
                Id = 6,
                Title = "运动健身",
                Description = "每周至少运动3次",
                IsCompleted = false,
                ProjectId = personalProject.Id,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            }
        };

        Todos.AddRange(todos);
        SaveChanges();

        // 更新 PostgreSQL 序列
        FixSequences();
    }

    /// <summary>
    /// 修复 PostgreSQL 序列，确保后续插入使用正确的 ID
    /// </summary>
    private void FixSequences()
    {
        // 更新 PostgreSQL 序列，确保后续插入使用正确的 ID
        // 这是必需的，因为手动插入固定 ID 后，序列不会自动更新
        // 使用 COALESCE 处理空表情况，确保序列至少设置为 1
        try
        {
            Database.ExecuteSqlRaw(@"
                SELECT setval('""Users_Id_seq""', COALESCE((SELECT MAX(""Id"") FROM ""Users""), 1), true);
                SELECT setval('""Projects_Id_seq""', COALESCE((SELECT MAX(""Id"") FROM ""Projects""), 1), true);
                SELECT setval('""Todos_Id_seq""', COALESCE((SELECT MAX(""Id"") FROM ""Todos""), 1), true);
            ");
        }
        catch
        {
            // 如果序列不存在或更新失败，忽略错误
            // 这通常发生在数据库结构不同或序列名称不同的情况下
        }
    }
}

