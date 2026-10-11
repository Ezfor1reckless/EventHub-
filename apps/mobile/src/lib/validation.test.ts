/// <reference types="jest" />

// 显式引用 jest 类型。
// expo/tsconfig.base 没有限制 types，@types/jest 也确实装在
// apps/mobile/node_modules/@types 下，但 tsc 就是没自动纳入它——
// 于是 describe/it/expect 全报 "Cannot find name"。
//
// 用三斜线引用而不是在 tsconfig 里写 "types": ["jest"]：
// 后者会关闭 @types 的自动纳入，可能连带影响 @types/react 等，
// 而它们目前是靠自动纳入工作的。只影响这一个文件，爆炸半径最小。

import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  validateLogin,
  validateRegistration,
} from './validation';

/**
 * 校验规则的单测。
 *
 * 这些规则必须与后端 RegisterRequest 保持一致，所以每一条边界都写下来——
 * 将来后端改了规则，这里的失败就是那个改动需要同步前端的信号。
 */
describe('validateRegistration', () => {
  const valid = {
    email: 'alice@example.com',
    nickname: '爱丽丝',
    password: 's3cretPass',
    phone: '',
  };

  it('合法输入没有任何错误', () => {
    expect(validateRegistration(valid)).toEqual({});
  });

  it('手机号选填，留空不报错', () => {
    expect(validateRegistration({ ...valid, phone: '' })).toEqual({});
    expect(validateRegistration({ ...valid, phone: undefined })).toEqual({});
  });

  it('合法手机号通过', () => {
    expect(validateRegistration({ ...valid, phone: '13800138000' })).toEqual({});
  });

  it('非法手机号被拒', () => {
    expect(validateRegistration({ ...valid, phone: '12345' })).toHaveProperty('phone');
    // 第二位不能是 2
    expect(validateRegistration({ ...valid, phone: '12800138000' })).toHaveProperty('phone');
  });

  describe('邮箱', () => {
    it.each([
      ['', '空'],
      ['   ', '全是空格'],
      ['not-an-email', '没有 @'],
      ['a@b', '没有顶级域名'],
      ['a b@example.com', '含空格'],
    ])('%s 被拒（%s）', (email) => {
      expect(validateRegistration({ ...valid, email })).toHaveProperty('email');
    });

    it('前后空格会被去掉后再判断', () => {
      expect(validateRegistration({ ...valid, email: '  alice@example.com  ' })).toEqual({});
    });
  });

  describe('昵称', () => {
    it('空昵称被拒', () => {
      expect(validateRegistration({ ...valid, nickname: '   ' })).toHaveProperty('nickname');
    });

    it('超长昵称被拒', () => {
      expect(validateRegistration({ ...valid, nickname: 'a'.repeat(65) })).toHaveProperty(
        'nickname',
      );
    });

    it('刚好 64 个字符通过（边界值）', () => {
      expect(validateRegistration({ ...valid, nickname: 'a'.repeat(64) })).toEqual({});
    });
  });

  describe('密码', () => {
    it('太短被拒', () => {
      expect(validateRegistration({ ...valid, password: 'a1b2c3' })).toHaveProperty('password');
    });

    it(`刚好 ${PASSWORD_MIN_LENGTH} 位且含字母数字，通过（边界值）`, () => {
      expect(validateRegistration({ ...valid, password: 'abcdefg1' })).toEqual({});
    });

    it('超过上限被拒', () => {
      const tooLong = `${'a'.repeat(PASSWORD_MAX_LENGTH)}1`;
      expect(validateRegistration({ ...valid, password: tooLong })).toHaveProperty('password');
    });

    it('纯字母被拒', () => {
      expect(validateRegistration({ ...valid, password: 'onlyletters' })).toHaveProperty(
        'password',
      );
    });

    it('纯数字被拒', () => {
      expect(validateRegistration({ ...valid, password: '12345678' })).toHaveProperty('password');
    });
  });

  it('多个字段同时出错时全部返回', () => {
    const errors = validateRegistration({
      email: 'bad',
      nickname: '',
      password: 'short',
      phone: '123',
    });
    expect(Object.keys(errors).sort()).toEqual(['email', 'nickname', 'password', 'phone']);
  });
});

describe('validateLogin', () => {
  it('填写完整时无错误', () => {
    expect(validateLogin({ email: 'alice@example.com', password: 'whatever' })).toEqual({});
  });

  it('登录不校验邮箱格式与密码强度', () => {
    // 登录只检查「填没填」：格式规则一旦收紧，历史账号就登不进来了。
    // 校验强度是注册时的事。
    expect(validateLogin({ email: 'not-an-email', password: '1' })).toEqual({});
  });

  it('空字段被拒', () => {
    const errors = validateLogin({ email: '  ', password: '' });
    expect(errors).toHaveProperty('email');
    expect(errors).toHaveProperty('password');
  });
});
