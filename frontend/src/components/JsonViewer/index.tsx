import React from 'react';
import JsonView from '@uiw/react-json-view';

interface JsonViewerProps {
  data: string | object | null | undefined;
  name?: string | false;
}

const JsonViewer: React.FC<JsonViewerProps> = ({ data }) => {
  if (!data) {
    return <span style={{ color: '#999' }}>无数据</span>;
  }

  let jsonData: object = {};
  
  if (typeof data === 'string') {
    try {
      jsonData = JSON.parse(data);
    } catch (e) {
      // 如果不是有效的 JSON 字符串，则直接显示文本
      return <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: 0 }}>{data}</pre>;
    }
  } else {
    jsonData = data;
  }

  return (
    <div style={{ maxHeight: '400px', overflow: 'auto', border: '1px solid #f0f0f0', borderRadius: '4px', padding: '8px' }}>
      <JsonView 
        value={jsonData} 
        collapsed={2}
        style={{ fontSize: '12px' }}
      />
    </div>
  );
};

export default JsonViewer;
