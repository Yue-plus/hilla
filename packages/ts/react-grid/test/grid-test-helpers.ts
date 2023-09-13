import type { Grid } from '@vaadin/grid';
// @ts-expect-error no types for the utils
import { getCellContent, getRowCells, getPhysicalItems } from './grid-test-utils.js';

export const getBodyCellContent = <T>(grid: Grid<T>, row: number, col: number): HTMLElement => {
  const physicalItems = getPhysicalItems(grid);
  // eslint-disable-next-line
  const physicalRow = physicalItems.find((item: any) => item.index === row);
  const cells = getRowCells(physicalRow);
  return getCellContent(cells[col]);
};
