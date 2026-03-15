import React from 'react';
import { Button } from 'antd';
import type { ButtonProps } from 'antd';

type Props = Omit<ButtonProps, 'type'>;

export const PrimaryButton: React.FC<Props> = (props) => (
  <Button type="primary" {...props} />
);

export const DangerButton: React.FC<Props> = (props) => (
  <Button danger {...props} />
);

