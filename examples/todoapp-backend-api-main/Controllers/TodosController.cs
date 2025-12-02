using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;
using TodoApp_backend.Data;
using TodoApp_backend.DTOs;
using TodoApp_backend.Models;

namespace TodoApp_backend.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class TodosController : ControllerBase
{
    private readonly ApplicationDbContext _context;

    public TodosController(ApplicationDbContext context)
    {
        _context = context;
    }

    private int GetUserId()
    {
        var userIdClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return int.Parse(userIdClaim ?? "0");
    }

    [HttpPost]
    public async Task<ActionResult<TodoDto>> CreateTodo(CreateTodoDto dto)
    {
        var userId = GetUserId();
        
        // 验证项目是否属于当前用户
        var project = await _context.Projects
            .FirstOrDefaultAsync(p => p.Id == dto.ProjectId && p.UserId == userId);

        if (project == null)
        {
            return NotFound(new { message = "项目不存在" });
        }

        var todo = new Todo
        {
            Title = dto.Title,
            Description = dto.Description,
            ProjectId = dto.ProjectId
        };

        _context.Todos.Add(todo);
        await _context.SaveChangesAsync();

        return CreatedAtAction(nameof(GetTodo), new { id = todo.Id }, new TodoDto
        {
            Id = todo.Id,
            Title = todo.Title,
            Description = todo.Description,
            IsCompleted = todo.IsCompleted,
            ProjectId = todo.ProjectId,
            CreatedAt = todo.CreatedAt,
            UpdatedAt = todo.UpdatedAt
        });
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<TodoDto>> GetTodo(int id)
    {
        var userId = GetUserId();
        var todo = await _context.Todos
            .Include(t => t.Project)
            .FirstOrDefaultAsync(t => t.Id == id && t.Project.UserId == userId);

        if (todo == null)
        {
            return NotFound();
        }

        return Ok(new TodoDto
        {
            Id = todo.Id,
            Title = todo.Title,
            Description = todo.Description,
            IsCompleted = todo.IsCompleted,
            ProjectId = todo.ProjectId,
            CreatedAt = todo.CreatedAt,
            UpdatedAt = todo.UpdatedAt
        });
    }

    [HttpPut("{id}")]
    public async Task<ActionResult<TodoDto>> UpdateTodo(int id, UpdateTodoDto dto)
    {
        var userId = GetUserId();
        var todo = await _context.Todos
            .Include(t => t.Project)
            .FirstOrDefaultAsync(t => t.Id == id && t.Project.UserId == userId);

        if (todo == null)
        {
            return NotFound();
        }

        todo.Title = dto.Title;
        todo.Description = dto.Description;
        todo.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync();

        return Ok(new TodoDto
        {
            Id = todo.Id,
            Title = todo.Title,
            Description = todo.Description,
            IsCompleted = todo.IsCompleted,
            ProjectId = todo.ProjectId,
            CreatedAt = todo.CreatedAt,
            UpdatedAt = todo.UpdatedAt
        });
    }

    [HttpDelete("{id}")]
    public async Task<IActionResult> DeleteTodo(int id)
    {
        var userId = GetUserId();
        var todo = await _context.Todos
            .Include(t => t.Project)
            .FirstOrDefaultAsync(t => t.Id == id && t.Project.UserId == userId);

        if (todo == null)
        {
            return NotFound();
        }

        _context.Todos.Remove(todo);
        await _context.SaveChangesAsync();

        return NoContent();
    }

    [HttpPatch("{id}/complete")]
    public async Task<IActionResult> ToggleComplete(int id)
    {
        var userId = GetUserId();
        var todo = await _context.Todos
            .Include(t => t.Project)
            .FirstOrDefaultAsync(t => t.Id == id && t.Project.UserId == userId);

        if (todo == null)
        {
            return NotFound();
        }

        todo.IsCompleted = !todo.IsCompleted;
        todo.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync();

        return Ok(new TodoDto
        {
            Id = todo.Id,
            Title = todo.Title,
            Description = todo.Description,
            IsCompleted = todo.IsCompleted,
            ProjectId = todo.ProjectId,
            CreatedAt = todo.CreatedAt,
            UpdatedAt = todo.UpdatedAt
        });
    }
}

