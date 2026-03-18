import type { Key } from 'react';
import type { DataNode } from 'antd/es/tree';

export interface PermissionTreeNodeLike {
  id: number | string;
  name?: string;
  permissionKey?: string | null;
  children?: PermissionTreeNodeLike[];
}

type CheckedKeysInput = Key[] | { checked?: Key[]; halfChecked?: Key[] };

export function normalizeCheckedKeys(keys: CheckedKeysInput): Key[] {
  return Array.isArray(keys) ? keys : (keys.checked || []);
}

export function toCheckablePermissionTreeData(nodes: PermissionTreeNodeLike[]): DataNode[] {
  return nodes.map((node) => {
    const children = node.children?.length ? toCheckablePermissionTreeData(node.children) : undefined;
    return {
      key: String(node.id),
      title: node.name || node.permissionKey || `节点${node.id}`,
      children,
      checkable: true,
    };
  });
}

export function collectGrantablePermissionIds(nodes: PermissionTreeNodeLike[]): number[] {
  const ids: number[] = [];
  const walk = (list: PermissionTreeNodeLike[]) => {
    for (const n of list) {
      if (n.permissionKey) {
        const id = Number(n.id);
        if (Number.isFinite(id)) ids.push(id);
      }
      if (n.children?.length) walk(n.children);
    }
  };
  walk(nodes);
  return ids;
}

export function toGrantableCheckedIds(nodes: PermissionTreeNodeLike[], checkedKeys: Key[]): number[] {
  const grantable = new Set(collectGrantablePermissionIds(nodes).map(String));
  const result: number[] = [];
  for (const key of checkedKeys) {
    const raw = String(key);
    if (grantable.has(raw)) {
      const id = Number(raw);
      if (Number.isFinite(id)) result.push(id);
    }
  }
  return result;
}
