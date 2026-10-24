import React, { useEffect, useState } from 'react';
import { 
  FolderKanban, 
  Plus, 
  Search, 
  Network, 
  Trash2, 
  Edit3, 
  ExternalLink, 
  Clock, 
  ShieldCheck, 
  AlertCircle,
  X
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import { Project, CreateProjectInput, UpdateProjectInput } from '../types';
import { EmptyState } from '../components/common/EmptyState';
import { Pagination } from '../components/common/Pagination';

interface ProjectsPageProps {
  onNavigateToEndpoints?: (projectId: string) => void;
}

export const ProjectsPage: React.FC<ProjectsPageProps> = ({ onNavigateToEndpoints }) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Pagination states
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 9;

  // Modals state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateProjectInput>({ name: '', slug: '', description: '' });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [editForm, setEditForm] = useState<UpdateProjectInput>({ name: '', description: '' });
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  const [deletingProject, setDeletingProject] = useState<Project | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const loadProjects = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await ApiClient.listProjects(search || undefined, currentPage, pageSize);
      setProjects(res.content);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements || 0);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch projects');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects();
  }, [search, currentPage]);

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createForm.name.trim()) {
      setCreateError('Project name is required.');
      return;
    }
    try {
      setCreateLoading(true);
      setCreateError(null);
      await ApiClient.createProject({
        name: createForm.name.trim(),
        slug: createForm.slug?.trim() || undefined,
        description: createForm.description?.trim() || undefined,
      });
      setIsCreateOpen(false);
      setCreateForm({ name: '', slug: '', description: '' });
      await loadProjects();
    } catch (err: any) {
      setCreateError(err.message || 'Failed to create project');
    } finally {
      setCreateLoading(false);
    }
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingProject || !editForm.name.trim()) {
      setEditError('Project name is required.');
      return;
    }
    try {
      setEditLoading(true);
      setEditError(null);
      await ApiClient.updateProject(editingProject.id, {
        name: editForm.name.trim(),
        description: editForm.description?.trim() || undefined,
      });
      setEditingProject(null);
      await loadProjects();
    } catch (err: any) {
      setEditError(err.message || 'Failed to update project');
    } finally {
      setEditLoading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingProject) return;
    try {
      setDeleteLoading(true);
      await ApiClient.deleteProject(deletingProject.id);
      setDeletingProject(null);
      await loadProjects();
    } catch (err: any) {
      setError(err.message || 'Failed to delete project');
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-white/5">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
            <FolderKanban className="w-5 h-5 text-cyan-400" />
            Projects & Workspaces
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Group APIs, enforce multi-tenant isolation, and govern role-based access.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search projects..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="input-field pl-9 pr-3 py-1.5 text-xs w-48 sm:w-64"
            />
          </div>
          <button
            type="button"
            onClick={() => {
              setCreateError(null);
              setIsCreateOpen(true);
            }}
            className="btn-primary text-xs font-mono"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>New Project</span>
          </button>
        </div>
      </div>

      {/* Global Error Banner */}
      {error && (
        <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-3">
          <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>{error}</span>
        </div>
      )}

      {/* Loading state */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {[1, 2, 3].map((i) => (
            <div key={i} className="glass-panel p-5 animate-pulse space-y-4">
              <div className="h-5 bg-white/5 rounded w-1/2" />
              <div className="h-4 bg-white/5 rounded w-3/4" />
              <div className="h-8 bg-white/5 rounded w-full mt-4" />
            </div>
          ))}
        </div>
      ) : projects.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title={search ? 'No Matching Projects' : 'No Projects Found'}
          description={
            search
              ? `No projects matched the search term "${search}". Clear the search query to see all workspaces.`
              : 'Create your first project workspace to start registering API endpoints and importing OpenAPI specifications.'
          }
          actionText="Create Project"
          onAction={() => setIsCreateOpen(true)}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {projects.map((project) => (
            <div
              key={project.id}
              className="glass-panel p-5 flex flex-col justify-between hover:border-cyan-500/30 transition-all group"
            >
              <div>
                <div className="flex items-start justify-between gap-3 mb-2">
                  <h3 className="font-semibold text-slate-100 text-base group-hover:text-cyan-300 transition-colors">
                    {project.name}
                  </h3>
                  <span className="badge badge-neutral text-[10px] font-mono tracking-wider flex items-center gap-1">
                    <ShieldCheck className="w-3 h-3 text-cyan-400" />
                    {project.userRole}
                  </span>
                </div>

                <div className="text-[11px] font-mono text-cyan-400/80 mb-3 bg-cyan-950/20 px-2 py-0.5 rounded border border-cyan-500/10 inline-block">
                  /{project.slug}
                </div>

                <p className="text-xs text-slate-400 line-clamp-2 mb-4 leading-relaxed">
                  {project.description || 'No description provided for this workspace.'}
                </p>
              </div>

              <div className="pt-4 border-t border-white/5 space-y-3">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="flex items-center gap-1.5 font-mono text-[11px]">
                    <Network className="w-3.5 h-3.5 text-cyan-400" />
                    <span className="font-semibold text-slate-200">{project.endpointCount}</span> endpoints
                  </span>
                  <span className="flex items-center gap-1 text-[11px] text-slate-500">
                    <Clock className="w-3 h-3" />
                    {new Date(project.createdAt).toLocaleDateString()}
                  </span>
                </div>

                <div className="flex items-center gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => onNavigateToEndpoints && onNavigateToEndpoints(project.id)}
                    className="flex-1 py-1.5 px-3 rounded-md bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/20 text-cyan-300 text-xs font-mono font-medium flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>Endpoints</span>
                  </button>

                  {project.userRole === 'OWNER' && (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          setEditingProject(project);
                          setEditForm({ name: project.name, description: project.description || '' });
                          setEditError(null);
                        }}
                        className="p-1.5 rounded-md hover:bg-white/10 text-slate-400 hover:text-slate-200 transition-colors"
                        title="Edit Project"
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeletingProject(project)}
                        className="p-1.5 rounded-md hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-colors"
                        title="Delete Project"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pagination */}
      <Pagination
        currentPage={currentPage}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={pageSize}
        onPageChange={(p) => setCurrentPage(p)}
      />

      {/* Create Project Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-md p-6 border-white/10 shadow-2xl relative">
            <button
              type="button"
              onClick={() => setIsCreateOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-bold text-slate-100 font-mono mb-1 flex items-center gap-2">
              <FolderKanban className="w-5 h-5 text-cyan-400" />
              Create Project
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Workspaces isolate APIs, test suites, and incident reports.
            </p>

            {createError && (
              <div className="mb-4 p-3 rounded bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs">
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Project Name <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  placeholder="e.g. Core Billing Service"
                  value={createForm.name}
                  onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                  className="input-field"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Slug (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. core-billing-service"
                  value={createForm.slug || ''}
                  onChange={(e) => setCreateForm({ ...createForm, slug: e.target.value })}
                  className="input-field font-mono text-xs"
                />
                <span className="text-[11px] text-slate-500 mt-1 block">
                  Leave blank to auto-generate from the project name.
                </span>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Description
                </label>
                <textarea
                  placeholder="Short description of this API service or workspace..."
                  value={createForm.description || ''}
                  onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
                  className="input-field h-24 resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createLoading}
                  className="btn-primary text-xs font-mono"
                >
                  {createLoading ? 'Creating...' : 'Create Project'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Project Modal */}
      {editingProject && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-md p-6 border-white/10 shadow-2xl relative">
            <button
              type="button"
              onClick={() => setEditingProject(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-bold text-slate-100 font-mono mb-1 flex items-center gap-2">
              <Edit3 className="w-5 h-5 text-cyan-400" />
              Edit Project
            </h3>
            <p className="text-xs text-slate-400 mb-5">
              Update workspace name and overview.
            </p>

            {editError && (
              <div className="mb-4 p-3 rounded bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs">
                {editError}
              </div>
            )}

            <form onSubmit={handleEditSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Project Name <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  className="input-field"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Description
                </label>
                <textarea
                  value={editForm.description || ''}
                  onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                  className="input-field h-24 resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setEditingProject(null)}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={editLoading}
                  className="btn-primary text-xs font-mono"
                >
                  {editLoading ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deletingProject && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-sm p-6 border-rose-500/30 shadow-2xl relative">
            <h3 className="text-base font-bold text-rose-300 font-mono mb-2 flex items-center gap-2">
              <Trash2 className="w-5 h-5 text-rose-400" />
              Delete Project?
            </h3>
            <p className="text-xs text-slate-300 mb-4 leading-relaxed">
              Are you sure you want to delete <span className="text-white font-semibold">"{deletingProject.name}"</span>?
              This will remove all associated endpoints and test configurations.
            </p>
            <div className="flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setDeletingProject(null)}
                className="btn-secondary text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeleteConfirm}
                disabled={deleteLoading}
                className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-medium font-mono transition-colors"
              >
                {deleteLoading ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
