import React, { useEffect, useState } from 'react';
import {
  PlayCircle,
  Play,
  Plus,
  Trash2,
  CheckCircle2,
  XCircle,
  Layers,
  Server,
  Check,
  X,
  Eye
} from 'lucide-react';
import { ApiClient } from '../services/apiClient';
import {
  Project,
  TestSuite,
  TestCase,
  TestRun,
  TestResult,
  AssertionDefinition,
  AssertionType
} from '../types';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorBanner } from '../components/common/ErrorBanner';
import { CardSkeleton, TableSkeleton } from '../components/common/LoadingSkeleton';

export const TestRunsPage: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'suites' | 'runs'>('suites');

  // Test Suites state
  const [testSuites, setTestSuites] = useState<TestSuite[]>([]);
  const [selectedSuite, setSelectedSuite] = useState<TestSuite | null>(null);
  const [suiteTestCases, setSuiteTestCases] = useState<TestCase[]>([]);
  const [loadingSuites, setLoadingSuites] = useState(false);
  const [loadingCases, setLoadingCases] = useState(false);

  // Test Runs state
  const [testRuns, setTestRuns] = useState<TestRun[]>([]);
  const [loadingRuns, setLoadingRuns] = useState(false);
  const [executingSuiteId, setExecutingSuiteId] = useState<string | null>(null);

  // Detailed Test Run Inspection
  const [inspectedRun, setInspectedRun] = useState<TestRun | null>(null);
  const [inspectedRunResults, setInspectedRunResults] = useState<TestResult[]>([]);
  const [loadingResults, setLoadingResults] = useState(false);

  // Modals state
  const [isCreateSuiteOpen, setIsCreateSuiteOpen] = useState(false);
  const [suiteName, setSuiteName] = useState('');
  const [suiteDescription, setSuiteDescription] = useState('');
  const [createSuiteLoading, setCreateSuiteLoading] = useState(false);

  const [isCreateCaseOpen, setIsCreateCaseOpen] = useState(false);
  const [caseName, setCaseName] = useState('');
  const [caseMethod, setCaseMethod] = useState('GET');
  const [caseTargetPath, setCaseTargetPath] = useState('');
  const [caseRequestBody, setCaseRequestBody] = useState('');
  const [caseAssertions, setCaseAssertions] = useState<AssertionDefinition[]>([
    { type: 'STATUS_CODE_EQUALS', expected: '200' },
    { type: 'RESPONSE_TIME_BELOW', expected: '2000' }
  ]);
  const [createCaseLoading, setCreateCaseLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Load projects on mount
  useEffect(() => {
    loadProjects();
  }, []);

  const loadProjects = async () => {
    try {
      setError(null);
      const res = await ApiClient.listProjects('', 0, 100);
      const projs = res.content || [];
      setProjects(projs);
      if (projs.length > 0 && !selectedProjectId) {
        setSelectedProjectId(projs[0].id);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch projects.');
    }
  };

  // When project changes, load suites and runs
  useEffect(() => {
    if (selectedProjectId) {
      loadSuites(selectedProjectId);
    }
  }, [selectedProjectId]);

  const loadSuites = async (projectId: string) => {
    try {
      setLoadingSuites(true);
      setError(null);
      const suites = await ApiClient.listTestSuites(projectId);
      setTestSuites(suites);

      if (suites.length > 0) {
        // If selected suite is not in list, select first
        if (!selectedSuite || !suites.some(s => s.id === selectedSuite.id)) {
          setSelectedSuite(suites[0]);
          loadCases(suites[0].id);
          loadRunsForSuite(suites[0].id);
        } else {
          loadCases(selectedSuite.id);
          loadRunsForSuite(selectedSuite.id);
        }
      } else {
        setSelectedSuite(null);
        setSuiteTestCases([]);
        setTestRuns([]);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch test suites.');
    } finally {
      setLoadingSuites(false);
    }
  };

  const loadCases = async (suiteId: string) => {
    try {
      setLoadingCases(true);
      const cases = await ApiClient.getTestCases(suiteId);
      setSuiteTestCases(cases);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch test cases.');
    } finally {
      setLoadingCases(false);
    }
  };

  const loadRunsForSuite = async (suiteId: string) => {
    try {
      setLoadingRuns(true);
      const runs = await ApiClient.getTestRunsBySuite(suiteId);
      setTestRuns(runs.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
    } catch (err: any) {
      setError(err.message || 'Failed to fetch test runs.');
    } finally {
      setLoadingRuns(false);
    }
  };

  const handleSelectSuite = (suite: TestSuite) => {
    setSelectedSuite(suite);
    loadCases(suite.id);
    loadRunsForSuite(suite.id);
  };

  // Create Test Suite
  const handleCreateSuite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProjectId || !suiteName.trim()) return;

    try {
      setCreateSuiteLoading(true);
      setError(null);
      const newSuite = await ApiClient.createTestSuite(selectedProjectId, {
        name: suiteName.trim(),
        description: suiteDescription.trim() || undefined,
      });
      setIsCreateSuiteOpen(false);
      setSuiteName('');
      setSuiteDescription('');
      setSuccessMessage(`Test suite "${newSuite.name}" created successfully.`);
      setTimeout(() => setSuccessMessage(null), 4000);
      await loadSuites(selectedProjectId);
      setSelectedSuite(newSuite);
    } catch (err: any) {
      setError(err.message || 'Failed to create test suite.');
    } finally {
      setCreateSuiteLoading(false);
    }
  };

  // Delete Test Suite
  const handleDeleteSuite = async (suiteId: string) => {
    if (!window.confirm('Are you sure you want to delete this test suite and all its test cases?')) return;
    try {
      await ApiClient.deleteTestSuite(suiteId);
      setSuccessMessage('Test suite deleted.');
      setTimeout(() => setSuccessMessage(null), 3000);
      await loadSuites(selectedProjectId);
    } catch (err: any) {
      setError(err.message || 'Failed to delete test suite.');
    }
  };

  // Add Test Case
  const handleCreateCase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSuite || !caseName.trim() || !caseTargetPath.trim()) return;

    try {
      setCreateCaseLoading(true);
      setError(null);
      await ApiClient.createTestCase(selectedSuite.id, {
        name: caseName.trim(),
        httpMethod: caseMethod,
        targetPath: caseTargetPath.trim(),
        requestBody: caseRequestBody.trim() || undefined,
        assertions: caseAssertions,
        executionOrder: suiteTestCases.length + 1,
      });

      setIsCreateCaseOpen(false);
      setCaseName('');
      setCaseTargetPath('');
      setCaseRequestBody('');
      setCaseAssertions([
        { type: 'STATUS_CODE_EQUALS', expected: '200' },
        { type: 'RESPONSE_TIME_BELOW', expected: '2000' }
      ]);
      setSuccessMessage('Test case added to suite.');
      setTimeout(() => setSuccessMessage(null), 3000);
      await loadCases(selectedSuite.id);
    } catch (err: any) {
      setError(err.message || 'Failed to add test case.');
    } finally {
      setCreateCaseLoading(false);
    }
  };

  // Delete Test Case
  const handleDeleteCase = async (caseId: string) => {
    if (!window.confirm('Delete this test case?')) return;
    try {
      await ApiClient.deleteTestCase(caseId);
      if (selectedSuite) {
        await loadCases(selectedSuite.id);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to delete test case.');
    }
  };

  // Trigger real Test Suite Execution
  const handleExecuteSuite = async (suiteId: string) => {
    try {
      setExecutingSuiteId(suiteId);
      setError(null);
      const run = await ApiClient.triggerTestRun(suiteId);
      setSuccessMessage(`Test Run #${run.id.slice(0, 8)} executed: ${run.passedTests}/${run.totalTests} passed (${run.status})!`);
      setTimeout(() => setSuccessMessage(null), 5000);
      await loadRunsForSuite(suiteId);
      setActiveTab('runs');
      handleInspectRun(run);
    } catch (err: any) {
      setError(err.message || 'Failed to execute test suite.');
    } finally {
      setExecutingSuiteId(null);
    }
  };

  // Inspect Run Results
  const handleInspectRun = async (run: TestRun) => {
    setInspectedRun(run);
    try {
      setLoadingResults(true);
      const results = await ApiClient.getTestRunResults(run.id);
      setInspectedRunResults(results);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch test run step details.');
    } finally {
      setLoadingResults(false);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Header and Project Selector */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-100 font-mono tracking-tight flex items-center gap-2">
            <PlayCircle className="w-5 h-5 text-cyan-400" />
            Automated Integration Test Suites & Runs
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Chain API tests with status code assertions, JSON response checks, and real-time latency thresholds.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Project selector */}
          {projects.length > 0 && (
            <div className="flex items-center gap-2 bg-[#0d131f] border border-white/10 rounded-lg px-3 py-1.5">
              <Server className="w-3.5 h-3.5 text-slate-400" />
              <select
                value={selectedProjectId}
                onChange={e => setSelectedProjectId(e.target.value)}
                aria-label="Select Project for Test Suites"
                className="bg-transparent text-xs font-mono text-slate-200 outline-none cursor-pointer"
              >
                {projects.map(p => (
                  <option key={p.id} value={p.id} className="bg-slate-900 text-slate-200">
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Create Suite Button */}
          <button
            type="button"
            onClick={() => setIsCreateSuiteOpen(true)}
            className="btn-primary text-xs font-mono"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Create Suite</span>
          </button>
        </div>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => selectedProjectId && loadSuites(selectedProjectId)} />}

      {successMessage && (
        <div className="p-3 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 text-xs font-mono flex items-center gap-2">
          <Check className="w-4 h-4 text-emerald-400" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-white/10">
        <button
          type="button"
          onClick={() => setActiveTab('suites')}
          className={`px-4 py-2 text-xs font-mono font-medium transition-all border-b-2 ${
            activeTab === 'suites'
              ? 'border-cyan-400 text-cyan-300 bg-cyan-500/5'
              : 'border-transparent text-slate-400 hover:text-white'
          }`}
        >
          Test Suites Catalog ({testSuites.length})
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('runs')}
          className={`px-4 py-2 text-xs font-mono font-medium transition-all border-b-2 ${
            activeTab === 'runs'
              ? 'border-cyan-400 text-cyan-300 bg-cyan-500/5'
              : 'border-transparent text-slate-400 hover:text-white'
          }`}
        >
          Historical Runs ({testRuns.length})
        </button>
      </div>

      {/* TAB 1: TEST SUITES & TEST CASES */}
      {activeTab === 'suites' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Suites List (Left Column) */}
          <div className="space-y-3">
            <h3 className="text-xs font-mono text-slate-400 uppercase tracking-wider">
              Configured Test Suites
            </h3>

            {loadingSuites ? (
              <CardSkeleton />
            ) : testSuites.length === 0 ? (
              <div className="glass-panel p-6 text-center text-xs text-slate-500 font-mono italic">
                No test suites configured in this project. Click "Create Suite" above to get started.
              </div>
            ) : (
              <div className="space-y-2">
                {testSuites.map(suite => {
                  const isSelected = selectedSuite?.id === suite.id;
                  const isExecuting = executingSuiteId === suite.id;
                  return (
                    <div
                      key={suite.id}
                      onClick={() => handleSelectSuite(suite)}
                      className={`glass-panel p-4 cursor-pointer transition-all ${
                        isSelected
                          ? 'border-cyan-500/50 bg-cyan-950/20 shadow-sm shadow-cyan-500/10'
                          : 'hover:border-white/20'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <div className="truncate">
                          <h4 className="text-sm font-bold text-white font-mono truncate">{suite.name}</h4>
                          {suite.description && (
                            <p className="text-xs text-slate-400 line-clamp-1">{suite.description}</p>
                          )}
                        </div>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-white/5 border border-white/10 text-cyan-300 shrink-0">
                          {suite.caseCount || 0} cases
                        </span>
                      </div>

                      <div className="flex items-center justify-between pt-2 border-t border-white/5 text-[11px] font-mono">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleExecuteSuite(suite.id);
                          }}
                          disabled={isExecuting}
                          className="btn-primary py-1 px-2.5 text-[11px]"
                        >
                          <Play className={`w-3 h-3 ${isExecuting ? 'animate-spin' : ''}`} />
                          <span>{isExecuting ? 'Running...' : 'Run Suite'}</span>
                        </button>

                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteSuite(suite.id);
                          }}
                          className="text-slate-500 hover:text-rose-400 transition-colors"
                          title="Delete Suite"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Test Cases in Selected Suite (Right 2 Columns) */}
          <div className="lg:col-span-2 space-y-4">
            {selectedSuite ? (
              <div className="glass-panel p-6 space-y-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-white/10">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-bold text-white font-mono">{selectedSuite.name}</h3>
                      <span className="text-xs px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 font-mono">
                        Active Suite
                      </span>
                    </div>
                    {selectedSuite.description && (
                      <p className="text-xs text-slate-400 mt-1">{selectedSuite.description}</p>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setIsCreateCaseOpen(true)}
                      className="btn-secondary text-xs font-mono"
                    >
                      <Plus className="w-3.5 h-3.5 text-cyan-400" />
                      <span>Add Test Case</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleExecuteSuite(selectedSuite.id)}
                      disabled={executingSuiteId === selectedSuite.id || suiteTestCases.length === 0}
                      className="btn-primary text-xs font-mono"
                    >
                      <Play className="w-3.5 h-3.5" />
                      <span>{executingSuiteId === selectedSuite.id ? 'Executing...' : 'Execute Suite'}</span>
                    </button>
                  </div>
                </div>

                {/* Test Cases List */}
                {loadingCases ? (
                  <TableSkeleton rows={3} cols={3} />
                ) : suiteTestCases.length === 0 ? (
                  <div className="p-8 text-center border border-dashed border-white/10 rounded-xl bg-black/20">
                    <Layers className="w-8 h-8 text-slate-500 mx-auto mb-2" />
                    <p className="text-xs text-slate-300 font-mono font-semibold">No test cases in this suite</p>
                    <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                      Click "Add Test Case" above to specify the HTTP method, endpoint URL, and assertions.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {suiteTestCases.map((tc, index) => (
                      <div
                        key={tc.id}
                        className="p-4 rounded-xl bg-white/[0.02] border border-white/5 space-y-3 hover:border-white/15 transition-all"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="w-5 h-5 rounded-full bg-cyan-500/10 text-cyan-400 font-mono text-[11px] flex items-center justify-center font-bold">
                              {index + 1}
                            </span>
                            <span className={`badge text-[10px] badge-${tc.httpMethod.toLowerCase()}`}>
                              {tc.httpMethod}
                            </span>
                            <span className="text-xs font-bold text-white font-mono">{tc.name}</span>
                          </div>

                          <button
                            type="button"
                            onClick={() => handleDeleteCase(tc.id)}
                            className="text-slate-500 hover:text-rose-400 transition-colors"
                            title="Delete Case"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>

                        <div className="text-xs font-mono text-slate-300 bg-black/30 p-2 rounded border border-white/5 truncate">
                          {tc.targetPath}
                        </div>

                        {/* Assertions */}
                        {tc.assertions && tc.assertions.length > 0 && (
                          <div className="pt-2 border-t border-white/5">
                            <span className="text-[10px] font-mono uppercase text-slate-400 block mb-1.5">
                              Configured Assertions ({tc.assertions.length}):
                            </span>
                            <div className="flex flex-wrap gap-1.5">
                              {tc.assertions.map((a, i) => (
                                <span
                                  key={i}
                                  className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-300"
                                >
                                  {a.type} {a.property ? `[${a.property}]` : ''} = {a.expected}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div className="glass-panel p-10 text-center text-xs text-slate-500 font-mono italic">
                Select a test suite from the left or create a new suite to view its cases.
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: HISTORICAL TEST RUNS & RESULTS */}
      {activeTab === 'runs' && (
        <div className="space-y-4">
          {loadingRuns ? (
            <TableSkeleton rows={5} cols={5} />
          ) : testRuns.length === 0 ? (
            <EmptyState
              icon={PlayCircle}
              title="No Test Runs Recorded"
              description="Execute a test suite to view execution timings, pass/fail counts, and step assertion breakdowns."
            />
          ) : (
            <div className="glass-panel overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs font-mono">
                  <thead className="bg-black/40 border-b border-white/5 text-slate-400 uppercase tracking-wider text-[11px]">
                    <tr>
                      <th className="p-3.5">Run Status</th>
                      <th className="p-3.5">Test Suite</th>
                      <th className="p-3.5">Pass / Fail Steps</th>
                      <th className="p-3.5">Duration</th>
                      <th className="p-3.5">Executed At</th>
                      <th className="p-3.5 text-right">Details</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {testRuns.map(run => {
                      const isPassed = run.status === 'PASSED';
                      return (
                        <tr key={run.id} className="hover:bg-white/[0.02] transition-colors">
                          <td className="p-3.5">
                            <span
                              className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                isPassed
                                  ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                                  : 'bg-rose-500/15 text-rose-400 border border-rose-500/30'
                              }`}
                            >
                              {isPassed ? <CheckCircle2 className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
                              {run.status}
                            </span>
                          </td>
                          <td className="p-3.5 text-white font-semibold">
                            {run.testSuiteName || 'Integration Test Suite'}
                          </td>
                          <td className="p-3.5 text-slate-300">
                            <span className="text-emerald-400 font-bold">{run.passedTests} passed</span> /{' '}
                            <span className={run.failedTests > 0 ? 'text-rose-400 font-bold' : 'text-slate-400'}>
                              {run.failedTests} failed
                            </span>{' '}
                            ({run.totalTests} total)
                          </td>
                          <td className="p-3.5 text-cyan-400">
                            {run.durationMs != null ? `${run.durationMs}ms` : 'N/A'}
                          </td>
                          <td className="p-3.5 text-slate-400">
                            {new Date(run.createdAt).toLocaleString()}
                          </td>
                          <td className="p-3.5 text-right">
                            <button
                              type="button"
                              onClick={() => handleInspectRun(run)}
                              className="btn-secondary py-1 px-2.5 text-[11px] font-mono"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              <span>Inspect Steps</span>
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* DRAWER: DETAILED TEST RUN STEP RESULTS */}
      {inspectedRun && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/70 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-2xl h-full p-6 border-l border-white/10 flex flex-col justify-between overflow-y-auto">
            <div>
              <div className="flex items-center justify-between pb-4 border-b border-white/10 mb-5">
                <div>
                  <h3 className="text-base font-bold text-white font-mono flex items-center gap-2">
                    Test Run Results #{inspectedRun.id.slice(0, 8)}
                  </h3>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Suite: {inspectedRun.testSuiteName} | Executed: {new Date(inspectedRun.createdAt).toLocaleString()}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setInspectedRun(null)}
                  className="text-slate-400 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Status Banner */}
              <div
                className={`p-4 rounded-xl border mb-5 flex items-center justify-between ${
                  inspectedRun.status === 'PASSED'
                    ? 'bg-emerald-950/30 border-emerald-500/30 text-emerald-300'
                    : 'bg-rose-950/30 border-rose-500/30 text-rose-300'
                }`}
              >
                <div className="flex items-center gap-3">
                  {inspectedRun.status === 'PASSED' ? (
                    <CheckCircle2 className="w-6 h-6 text-emerald-400" />
                  ) : (
                    <XCircle className="w-6 h-6 text-rose-400" />
                  )}
                  <div>
                    <span className="font-bold font-mono text-sm block">
                      Execution {inspectedRun.status}
                    </span>
                    <span className="text-xs opacity-80">
                      {inspectedRun.passedTests} passed of {inspectedRun.totalTests} total test steps
                    </span>
                  </div>
                </div>
                {inspectedRun.durationMs != null && (
                  <span className="text-xs font-mono font-bold">{inspectedRun.durationMs}ms duration</span>
                )}
              </div>

              {/* Step Results */}
              <div className="space-y-4">
                <h4 className="text-xs font-mono uppercase text-slate-400 tracking-wider">
                  Individual Step Results ({inspectedRunResults.length})
                </h4>

                {loadingResults ? (
                  <TableSkeleton rows={3} cols={2} />
                ) : inspectedRunResults.length === 0 ? (
                  <div className="p-4 text-center text-xs text-slate-500 font-mono italic">
                    No granular results recorded for this run.
                  </div>
                ) : (
                  inspectedRunResults.map((result, idx) => (
                    <div
                      key={result.id}
                      className="p-4 rounded-xl bg-white/[0.02] border border-white/5 space-y-3"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-5 h-5 rounded-full bg-white/5 text-slate-300 text-[11px] font-mono flex items-center justify-center font-bold">
                            {idx + 1}
                          </span>
                          <span className="text-xs font-bold text-white font-mono">
                            {result.testCaseName}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 text-xs font-mono">
                          {result.httpStatusCode && (
                            <span className="px-2 py-0.5 rounded bg-black/40 border border-white/5 text-slate-300">
                              HTTP {result.httpStatusCode}
                            </span>
                          )}
                          <span className="text-cyan-400">{result.responseTimeMs}ms</span>
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              result.status === 'PASSED'
                                ? 'bg-emerald-500/15 text-emerald-400'
                                : 'bg-rose-500/15 text-rose-400'
                            }`}
                          >
                            {result.status}
                          </span>
                        </div>
                      </div>

                      {result.errorMessage && (
                        <div className="p-2.5 rounded bg-rose-950/40 border border-rose-500/30 text-rose-300 text-xs font-mono">
                          {result.errorMessage}
                        </div>
                      )}

                      {/* Assertion Details Table */}
                      {result.assertionResults && result.assertionResults.length > 0 && (
                        <div className="pt-2 border-t border-white/5">
                          <span className="text-[10px] font-mono uppercase text-slate-400 block mb-1">
                            Assertion Evaluations:
                          </span>
                          <div className="space-y-1">
                            {result.assertionResults.map((ar, i) => (
                              <div
                                key={i}
                                className="flex items-center justify-between p-2 rounded bg-black/30 border border-white/5 text-xs font-mono"
                              >
                                <div className="flex items-center gap-2">
                                  {ar.passed ? (
                                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                                  ) : (
                                    <X className="w-3.5 h-3.5 text-rose-400" />
                                  )}
                                  <span className="text-slate-200">{ar.type}</span>
                                  {ar.property && <span className="text-slate-400">({ar.property})</span>}
                                </div>
                                <div className="text-[11px] text-slate-400">
                                  Expected: <span className="text-white">{ar.expected}</span>
                                  {ar.actual !== undefined && (
                                    <span> | Actual: <span className="text-white">{ar.actual}</span></span>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="pt-4 border-t border-white/5 flex justify-end">
              <button
                type="button"
                onClick={() => setInspectedRun(null)}
                className="btn-secondary text-xs"
              >
                Close Drawer
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: CREATE TEST SUITE */}
      {isCreateSuiteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-md p-6 border-white/10 shadow-2xl relative">
            <button
              type="button"
              onClick={() => setIsCreateSuiteOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-white font-mono mb-1 flex items-center gap-2">
              <PlayCircle className="w-5 h-5 text-cyan-400" />
              Create Test Suite
            </h3>
            <p className="text-xs text-slate-400 mb-4">
              Group endpoint requests into a sequential automated test flow.
            </p>

            <form onSubmit={handleCreateSuite} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">Suite Name *</label>
                <input
                  type="text"
                  value={suiteName}
                  onChange={e => setSuiteName(e.target.value)}
                  placeholder="e.g. User Authentication & Profile Suite"
                  required
                  className="input-field text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">Description</label>
                <textarea
                  value={suiteDescription}
                  onChange={e => setSuiteDescription(e.target.value)}
                  placeholder="Validates login, JWT generation, and profile retrieval..."
                  rows={3}
                  className="input-field text-xs resize-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsCreateSuiteOpen(false)}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createSuiteLoading || !suiteName.trim()}
                  className="btn-primary text-xs font-mono"
                >
                  {createSuiteLoading ? 'Creating...' : 'Create Suite'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: ADD TEST CASE */}
      {isCreateCaseOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-fadeIn">
          <div className="glass-panel w-full max-w-lg p-6 border-white/10 shadow-2xl relative max-h-[90vh] overflow-y-auto">
            <button
              type="button"
              onClick={() => setIsCreateCaseOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-white font-mono mb-1 flex items-center gap-2">
              <Plus className="w-5 h-5 text-cyan-400" />
              Add Test Case to Suite
            </h3>
            <p className="text-xs text-slate-400 mb-4">
              Define the request path and assertions to check against response attributes.
            </p>

            <form onSubmit={handleCreateCase} className="space-y-4">
              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">Case Name *</label>
                <input
                  type="text"
                  value={caseName}
                  onChange={e => setCaseName(e.target.value)}
                  placeholder="e.g. Verify Health Endpoint Returns 200 UP"
                  required
                  className="input-field text-xs font-mono"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-mono text-slate-300 mb-1">HTTP Method</label>
                  <select
                    value={caseMethod}
                    onChange={e => setCaseMethod(e.target.value)}
                    className="input-field text-xs font-mono cursor-pointer"
                  >
                    {['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map(m => (
                      <option key={m} value={m} className="bg-slate-900 text-white">
                        {m}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="col-span-2">
                  <label className="block text-xs font-mono text-slate-300 mb-1">Target Path / URL *</label>
                  <input
                    type="text"
                    value={caseTargetPath}
                    onChange={e => setCaseTargetPath(e.target.value)}
                    placeholder="/api/v1/health or https://api.example.com"
                    required
                    className="input-field text-xs font-mono"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-mono text-slate-300 mb-1">Request Body (Optional JSON)</label>
                <textarea
                  value={caseRequestBody}
                  onChange={e => setCaseRequestBody(e.target.value)}
                  placeholder='{"email": "user@example.com"}'
                  rows={2}
                  className="input-field text-xs font-mono resize-none"
                />
              </div>

              {/* Assertions Builder */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-mono text-slate-300">Assertions to Validate</label>
                  <button
                    type="button"
                    onClick={() => {
                      setCaseAssertions([
                        ...caseAssertions,
                        { type: 'STATUS_CODE_EQUALS', expected: '200' }
                      ]);
                    }}
                    className="text-xs text-cyan-400 hover:text-cyan-300 font-mono flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" />
                    <span>Add Assertion</span>
                  </button>
                </div>

                <div className="space-y-2">
                  {caseAssertions.map((assertion, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded-lg bg-black/40 border border-white/5 flex items-center gap-2"
                    >
                      <select
                        value={assertion.type}
                        onChange={e => {
                          const updated = [...caseAssertions];
                          updated[idx].type = e.target.value as AssertionType;
                          setCaseAssertions(updated);
                        }}
                        className="bg-slate-900 border border-white/10 rounded px-2 py-1 text-[11px] font-mono text-slate-200 outline-none flex-1"
                      >
                        <option value="STATUS_CODE_EQUALS">STATUS_CODE_EQUALS</option>
                        <option value="RESPONSE_TIME_BELOW">RESPONSE_TIME_BELOW</option>
                        <option value="HEADER_EXISTS">HEADER_EXISTS</option>
                        <option value="CONTENT_TYPE_MATCHES">CONTENT_TYPE_MATCHES</option>
                        <option value="JSON_FIELD_EXISTS">JSON_FIELD_EXISTS</option>
                        <option value="JSON_FIELD_EQUALS">JSON_FIELD_EQUALS</option>
                      </select>

                      <input
                        type="text"
                        value={assertion.expected || ''}
                        onChange={e => {
                          const updated = [...caseAssertions];
                          updated[idx].expected = e.target.value;
                          setCaseAssertions(updated);
                        }}
                        placeholder="Expected value"
                        className="bg-slate-900 border border-white/10 rounded px-2 py-1 text-[11px] font-mono text-slate-200 outline-none w-28"
                      />

                      <button
                        type="button"
                        onClick={() => {
                          setCaseAssertions(caseAssertions.filter((_, i) => i !== idx));
                        }}
                        className="text-slate-500 hover:text-rose-400"
                        title="Remove Assertion"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setIsCreateCaseOpen(false)}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createCaseLoading || !caseName.trim() || !caseTargetPath.trim()}
                  className="btn-primary text-xs font-mono"
                >
                  {createCaseLoading ? 'Adding...' : 'Save Test Case'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
