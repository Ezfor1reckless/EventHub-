/**
 * 表单校验规则。
 *
 * 从组件里抽出来有两个原因：一是能单独测试（组件内联的校验只能靠渲染整个页面来测），
 * 二是登录和注册两个页面用的是同一套规则，写在两处迟早会漂移。
 *
 * 规则必须与后端 DTO 保持一致（RegisterRequest / LoginRequest）。
 * 前端校验只为即时反馈，不是安全边界——后端仍然会独立校验一遍，
 * 所以这里宽松一点不会造成安全问题，严格一点则可能把合法输入挡在门外。
 */

export interface RegistrationInput {
  email: string;
  nickname: string;
  password: string;
  phone?: string;
}

export type FieldErrors = Record<string, string>;

export const PASSWORD_MIN_LENGTH = 8;
/** BCrypt 只取前 72 字节，更长的部分会被静默忽略，所以后端也限制到 72。 */
export const PASSWORD_MAX_LENGTH = 72;
export const NICKNAME_MAX_LENGTH = 64;

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const PHONE_PATTERN = /^1[3-9]\d{9}$/;
/** 至少一个字母 + 至少一个数字 */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/;

export function validateRegistration(input: RegistrationInput): FieldErrors {
  const errors: FieldErrors = {};

  const email = input.email.trim();
  if (!email) {
    errors.email = "请输入邮箱";
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = "邮箱格式不正确";
  }

  const nickname = input.nickname.trim();
  if (!nickname) {
    errors.nickname = "请输入昵称";
  } else if (nickname.length > NICKNAME_MAX_LENGTH) {
    errors.nickname = `昵称不能超过 ${NICKNAME_MAX_LENGTH} 个字符`;
  }

  if (input.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `密码至少 ${PASSWORD_MIN_LENGTH} 位`;
  } else if (input.password.length > PASSWORD_MAX_LENGTH) {
    errors.password = `密码不能超过 ${PASSWORD_MAX_LENGTH} 位`;
  } else if (!PASSWORD_PATTERN.test(input.password)) {
    errors.password = "密码需同时包含字母和数字";
  }

  // 手机号选填：空字符串视为「没填」，不报错
  if (input.phone && !PHONE_PATTERN.test(input.phone)) {
    errors.phone = "手机号格式不正确";
  }

  return errors;
}

export function validateLogin(input: { email: string; password: string }): FieldErrors {
  const errors: FieldErrors = {};
  if (!input.email.trim()) {
    errors.email = "请输入邮箱";
  }
  if (!input.password) {
    errors.password = "请输入密码";
  }
  return errors;
}
