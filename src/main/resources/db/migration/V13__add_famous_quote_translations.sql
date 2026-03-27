ALTER TABLE famous_quotes
ADD COLUMN IF NOT EXISTS quote_translation TEXT;

UPDATE famous_quotes
SET quote_translation = '未来属于那些相信梦想之美的人。'
WHERE quote_text = 'The future belongs to those who believe in the beauty of their dreams.'
  AND author = 'Eleanor Roosevelt';

UPDATE famous_quotes
SET quote_translation = '成功不是终点，失败也并非致命，重要的是继续前行的勇气。'
WHERE quote_text = 'Success is not final, failure is not fatal: it is the courage to continue that counts.'
  AND author = 'Winston Churchill';

UPDATE famous_quotes
SET quote_translation = '事情在做成之前，看起来总像是不可能的。'
WHERE quote_text = 'It always seems impossible until it is done.'
  AND author = 'Nelson Mandela';

UPDATE famous_quotes
SET quote_translation = '成就伟大事业的唯一方法，就是热爱你所做的事。'
WHERE quote_text = 'The only way to do great work is to love what you do.'
  AND author = 'Steve Jobs';

UPDATE famous_quotes
SET quote_translation = '困难之中，往往蕴藏着机遇。'
WHERE quote_text = 'In the middle of difficulty lies opportunity.'
  AND author = 'Albert Einstein';

UPDATE famous_quotes
SET quote_translation = '学习从不会使头脑疲惫。'
WHERE quote_text = 'Learning never exhausts the mind.'
  AND author = 'Leonardo da Vinci';

UPDATE famous_quotes
SET quote_translation = '我听见便忘记，我看见便记住，我做过便理解。'
WHERE quote_text = 'I hear and I forget. I see and I remember. I do and I understand.'
  AND author = 'Confucius';

UPDATE famous_quotes
SET quote_translation = '做得好，胜过说得好。'
WHERE quote_text = 'Well done is better than well said.'
  AND author = 'Benjamin Franklin';

UPDATE famous_quotes
SET quote_translation = '领先一步的秘诀，就是立刻开始。'
WHERE quote_text = 'The secret of getting ahead is getting started.'
  AND author = 'Mark Twain';

UPDATE famous_quotes
SET quote_translation = '心之所想，终将成为什么样的人。'
WHERE quote_text = 'What we think, we become.'
  AND author = 'Buddha';
